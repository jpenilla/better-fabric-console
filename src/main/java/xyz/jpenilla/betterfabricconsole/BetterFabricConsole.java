/*
 * This file is part of Better Fabric Console, licensed under the MIT License.
 *
 * Copyright (c) 2021-2022 Jason Penilla
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package xyz.jpenilla.betterfabricconsole;

import ca.stellardrift.confabricate.Confabricate;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;
import io.papermc.paper.console.HexFormattingConverter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.platform.fabric.FabricServerAudiences;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.ComponentSerializer;
import net.minecraft.DefaultUncaughtExceptionHandler;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.dedicated.DedicatedServer;
import org.apache.logging.log4j.core.config.plugins.util.PluginRegistry;
import org.apache.logging.log4j.core.config.plugins.util.PluginType;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.reference.ConfigurationReference;
import org.spongepowered.configurate.reference.ValueReference;
import xyz.jpenilla.betterfabricconsole.adventure.LoggingComponentSerializerHolder;
import xyz.jpenilla.betterfabricconsole.remap.MappingsCache;
import xyz.jpenilla.betterfabricconsole.remap.RemapMode;
import xyz.jpenilla.betterfabricconsole.remap.Remapper;

import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.GRAY;
import static net.kyori.adventure.text.format.TextColor.color;
import static net.kyori.adventure.text.format.TextDecoration.BOLD;
import static net.kyori.adventure.text.format.TextDecoration.ITALIC;
import static net.minecraft.commands.Commands.literal;

public final class BetterFabricConsole implements ModInitializer {
  public static final Logger LOGGER = LogUtils.getLogger();
  private static BetterFabricConsole instance;
  private Config config;
  private ModContainer modContainer;
  private volatile @Nullable DedicatedServer server;

  public BetterFabricConsole() {
    try {
      loadPluginsFromClassLoader(HexFormattingConverter.class.getClassLoader());
    } catch (final ReflectiveOperationException e) {
      LOGGER.error("Failed to load extra Log4j2 plugins", e);
    }
  }

  @SuppressWarnings("unchecked")
  private static void loadPluginsFromClassLoader(final ClassLoader loader) throws ReflectiveOperationException {
    final PluginRegistry registry = PluginRegistry.getInstance();
    final Method decodeCacheFiles = PluginRegistry.class.getDeclaredMethod("decodeCacheFiles", ClassLoader.class);
    decodeCacheFiles.setAccessible(true);
    final Map<String, List<PluginType<?>>> newPlugins =
      (Map<String, List<PluginType<?>>>) decodeCacheFiles.invoke(registry, loader);
    final Field pluginsByCategoryRefField = PluginRegistry.class.getDeclaredField("pluginsByCategoryRef");
    pluginsByCategoryRefField.setAccessible(true);
    final AtomicReference<Map<String, List<PluginType<?>>>> pluginsByCategoryRef =
      (AtomicReference<Map<String, List<PluginType<?>>>>) pluginsByCategoryRefField.get(registry);
    newPlugins.forEach((category, discoveredPlugins) -> {
      final Map<String, List<PluginType<?>>> plugins = pluginsByCategoryRef.get();

      final List<PluginType<?>> forCategory = plugins.computeIfAbsent(category, c -> discoveredPlugins);

      if (forCategory != discoveredPlugins) {
        for (final PluginType<?> pluginType : discoveredPlugins) {
          if (!forCategory.contains(pluginType)) {
            forCategory.add(pluginType);
          }
        }
      }
    });
  }

  public static @Nullable BetterFabricConsole instanceOrNull() {
    return instance;
  }

  public static BetterFabricConsole instance() {
    if (instance == null) {
      throw new IllegalStateException("Better Fabric Console has not yet been initialized!");
    }
    return instance;
  }

  @Override
  public void onInitialize() {
    instance = this;
    this.modContainer = FabricLoader.getInstance().getModContainer("better-fabric-console")
      .orElseThrow(() -> new IllegalStateException("Could not find mod container for better-fabric-console"));

    this.loadModConfig();

    CommandRegistrationCallback.EVENT.register(this::registerCommands);
    ServerLifecycleEvents.SERVER_STARTING.register(server -> this.server = (DedicatedServer) server);
    ServerLifecycleEvents.SERVER_STOPPED.register(server -> this.server = null);

    final @Nullable Remapper remapper = this.createRemapper();
    this.initConsoleThread(remapper);
  }

  private void initConsoleThread(final @Nullable Remapper remapper) {
    BetterFabricConsole.LOGGER.info("Initializing Better Fabric Console console thread...");
    final ConsoleThread consoleThread = new ConsoleThread(() -> this.server);
    consoleThread.setDaemon(true);
    consoleThread.setUncaughtExceptionHandler(new DefaultUncaughtExceptionHandler(LOGGER));
    consoleThread.init(remapper);
    consoleThread.start();
  }

  private @Nullable Remapper createRemapper() {
    if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
      LOGGER.info("Skipping Better Fabric Console mappings initialization, we are in a development environment (already mapped).");
      return null;
    }
    if (this.config.remapMode() == RemapMode.NONE) {
      return null;
    }

    LOGGER.info("Initializing Better Fabric Console mappings...");
    final MappingsCache mappingsCache = new MappingsCache(
      FabricLoader.getInstance().getGameDir().resolve("better-fabric-console/mappings-cache")
    );
    return this.config.remapMode().createRemapper(mappingsCache);
  }

  private void registerCommands(
    final CommandDispatcher<CommandSourceStack> dispatcher,
    final CommandBuildContext commandBuildContext,
    final Commands.CommandSelection commandSelection
  ) {
    dispatcher.register(literal("better-fabric-console")
      .requires(stack -> stack.hasPermission(stack.getServer().getOperatorUserPermissionLevel()))
      .executes(this::executeCommand));
  }

  private static final TextColor PINK = color(0xFF79C6);

  private int executeCommand(final CommandContext<CommandSourceStack> ctx) {
    final Audience audience = FabricServerAudiences.of(ctx.getSource().getServer()).audience(ctx.getSource());
    audience.sendMessage(text()
      .color(GRAY)
      .append(text("Better Fabric Console", PINK, BOLD))
      .append(text().content(" v").decorate(ITALIC))
      .append(text(this.modContainer.getMetadata().getVersion().getFriendlyString())));
    return Command.SINGLE_SUCCESS;
  }

  private void loadModConfig() {
    try (final ConfigurationReference<CommentedConfigurationNode> nodeRef = Confabricate.configurationFor(this.modContainer, false)) {
      final ValueReference<Config, CommentedConfigurationNode> configRef = nodeRef.referenceTo(Config.class);
      this.config = configRef.get();
      configRef.setAndSave(this.config);
    } catch (final ConfigurateException ex) {
      throw new RuntimeException("Failed to load config", ex);
    }
  }

  public Config config() {
    return this.config;
  }

  public @Nullable ComponentSerializer<Component, TextComponent, String> loggingComponentSerializer() {
    if (this.server == null) {
      return null;
    }
    return ((LoggingComponentSerializerHolder) this.server).loggingComponentSerializer();
  }
}
