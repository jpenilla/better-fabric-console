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

import com.mojang.logging.LogUtils;
import io.papermc.paper.console.HexFormattingConverter;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import org.apache.logging.log4j.core.config.plugins.util.PluginRegistry;
import org.apache.logging.log4j.core.config.plugins.util.PluginType;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.framework.qual.DefaultQualifier;
import org.slf4j.Logger;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;
import xyz.jpenilla.betterfabricconsole.configuration.Config;
import xyz.jpenilla.betterfabricconsole.console.ConsoleSetup;
import xyz.jpenilla.betterfabricconsole.console.ConsoleState;
import xyz.jpenilla.betterfabricconsole.remap.MappingsCache;
import xyz.jpenilla.betterfabricconsole.remap.RemapMode;
import xyz.jpenilla.betterfabricconsole.remap.Remapper;

@DefaultQualifier(NonNull.class)
public final class BetterFabricConsolePreLaunch implements PreLaunchEntrypoint {
  private static final Logger LOGGER = LogUtils.getLogger();
  static @MonotonicNonNull BetterFabricConsolePreLaunch INSTANCE;

  @MonotonicNonNull ModContainer modContainer;
  @MonotonicNonNull Config config;
  @MonotonicNonNull ConsoleState consoleState;

  @Override
  public void onPreLaunch() {
    try {
      loadPluginsFromClassLoader(HexFormattingConverter.class.getClassLoader());
    } catch (final ReflectiveOperationException e) {
      LOGGER.error("Failed to load extra Log4j2 plugins", e);
    }

    this.modContainer = FabricLoader.getInstance().getModContainer("better-fabric-console")
      .orElseThrow(() -> new IllegalStateException("Could not find mod container for better-fabric-console"));
    this.loadModConfig();
    INSTANCE = this;
    this.initConsole();
  }

  private void loadModConfig() {
    final Path configFile = FabricLoader.getInstance().getConfigDir()
      .resolve(this.modContainer.getMetadata().getId() + ".conf");
    final HoconConfigurationLoader loader = HoconConfigurationLoader.builder()
      .path(configFile)
      .build();
    try {
      if (!Files.exists(configFile.getParent())) {
        Files.createDirectories(configFile.getParent());
      }
      final CommentedConfigurationNode load = loader.load();
      this.config = load.get(Config.class);
      loader.save(loader.createNode(node -> node.set(this.config)));
    } catch (final IOException ex) {
      throw new RuntimeException("Failed to load config", ex);
    }
  }

  public Config config() {
    if (this.config == null) {
      throw new IllegalStateException("Config not loaded!");
    }
    return this.config;
  }

  private void initConsole() {
    LOGGER.info("Initializing Better Fabric Console...");
    final @Nullable Remapper remapper = this.createRemapper();
    this.consoleState = ConsoleSetup.init(remapper, this.config);
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

  @SuppressWarnings("unchecked")
  private static void loadPluginsFromClassLoader(final ClassLoader loader) throws ReflectiveOperationException {
    final PluginRegistry registry = PluginRegistry.getInstance();
    final Method decodeCacheFiles = PluginRegistry.class.getDeclaredMethod("decodeCacheFiles", ClassLoader.class);
    decodeCacheFiles.setAccessible(true);
    final Map<String, List<PluginType<?>>> newPlugins =
      (Map<String, List<PluginType<?>>>) decodeCacheFiles.invoke(registry, loader);
    final Map<String, List<PluginType<?>>> pluginsByCategory = registry.loadFromMainClassLoader();
    newPlugins.forEach((category, discoveredPlugins) -> {
      final List<PluginType<?>> forCategory = pluginsByCategory.computeIfAbsent(category, c -> discoveredPlugins);

      // New category
      if (forCategory == discoveredPlugins) {
        return;
      }

      // Existing category
      for (final PluginType<?> pluginType : discoveredPlugins) {
        if (!forCategory.contains(pluginType)) {
          forCategory.add(pluginType);
        }
      }
    });
  }
  public static @Nullable BetterFabricConsolePreLaunch instanceOrNull() {
    return INSTANCE;
  }

  public static BetterFabricConsolePreLaunch instance() {
    if (INSTANCE == null) {
      throw new IllegalStateException("BetterFabricConsolePreLaunch has not yet been initialized!");
    }
    return INSTANCE;
  }
}
