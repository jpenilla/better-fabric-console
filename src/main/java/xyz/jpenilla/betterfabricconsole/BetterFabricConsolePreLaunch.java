/*
 * This file is part of Better Fabric Console, licensed under the MIT License.
 *
 * Copyright (c) 2021-2024 Jason Penilla
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
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.plugins.util.PluginRegistry;
import org.apache.logging.log4j.core.config.plugins.util.PluginType;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;
import xyz.jpenilla.betterfabricconsole.configuration.Config;
import xyz.jpenilla.betterfabricconsole.console.ConsoleSetup;
import xyz.jpenilla.betterfabricconsole.console.ConsoleState;
import xyz.jpenilla.endermux.log4j.HexFormattingConverter;

import static java.util.Objects.requireNonNull;

@NullMarked
public final class BetterFabricConsolePreLaunch implements PreLaunchEntrypoint {
  private static final Logger LOGGER = LogUtils.getLogger();
  private static @Nullable BetterFabricConsolePreLaunch INSTANCE;

  private @Nullable ModContainer modContainer;
  private @Nullable Config config;
  private @Nullable ConsoleState consoleState;

  @Override
  public void onPreLaunch() {
    INSTANCE = this;
    try {
      loadPluginsFromClassLoader(HexFormattingConverter.class.getClassLoader());
    } catch (final ReflectiveOperationException e) {
      LOGGER.error("Failed to load extra Log4j2 plugins", e);
    }

    this.modContainer = FabricLoader.getInstance().getModContainer("better-fabric-console")
      .orElseThrow(() -> new IllegalStateException("Could not find mod container for better-fabric-console"));
    this.loadModConfig();
    this.extractLog4jConfig();
    this.initConsole();
    Configurator.reconfigure(this.log4jConfigPath().toUri());
    if (this.config().endermux().enabled()) {
      this.consoleState().endermux().start(this.config());
    }
  }

  private Path configDir() {
    final Path dir = FabricLoader.getInstance().getConfigDir()
      .resolve(this.modContainer().getMetadata().getId());
    if (!Files.exists(dir)) {
      try {
        Files.createDirectories(dir);
      } catch (final IOException ex) {
        throw new RuntimeException("Failed to create config directory", ex);
      }
    }
    if (!Files.isDirectory(dir)) {
      throw new IllegalStateException("Config directory is not a directory!");
    }
    return dir;
  }

  private Path log4jConfigPath() {
    return this.configDir().resolve("log4j2.xml");
  }

  private void loadModConfig() {
    final Path oldConfigFile = FabricLoader.getInstance().getConfigDir()
      .resolve(this.modContainer().getMetadata().getId() + ".conf");

    final Path configFile = this.configDir()
      .resolve(this.modContainer().getMetadata().getId() + ".conf");

    if (Files.exists(oldConfigFile)) {
      if (Files.exists(configFile)) {
        LOGGER.warn("Both {} and {} exist, using {}", oldConfigFile, configFile, configFile);
      } else {
        try {
          Files.move(oldConfigFile, configFile);
        } catch (final IOException ex) {
          throw new RuntimeException("Failed to migrate old config file", ex);
        }
      }
    }

    final HoconConfigurationLoader loader = HoconConfigurationLoader.builder()
      .path(configFile)
      .build();
    try {
      final CommentedConfigurationNode load = loader.load();
      this.config = load.get(Config.class);
      loader.save(loader.createNode(node -> node.set(this.config)));
    } catch (final IOException ex) {
      throw new RuntimeException("Failed to load config", ex);
    }
  }

  private void extractLog4jConfig() {
    final Path targetPath = this.log4jConfigPath();
    if (Files.isRegularFile(targetPath)) {
      // Already extracted
      return;
    }

    final Path log4jConfigPath = this.modContainer().findPath("better-fabric-console-default-log4j2.xml")
      .orElseThrow(() -> new IllegalStateException("Could not find better-fabric-console-default-log4j2.xml in mod container"));

    try (final InputStream inputStream = Files.newInputStream(log4jConfigPath)) {
      Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
    } catch (final IOException ex) {
      throw new RuntimeException("Failed to extract better-fabric-console-default-log4j2.xml", ex);
    }
  }

  public Config config() {
    if (this.config == null) {
      throw new IllegalStateException("Config not loaded!");
    }
    return this.config;
  }

  public ModContainer modContainer() {
    return requireNonNull(this.modContainer);
  }

  public ConsoleState consoleState() {
    return requireNonNull(this.consoleState);
  }

  private void initConsole() {
    LOGGER.info("Initializing Better Fabric Console...");
    this.consoleState = ConsoleSetup.init();
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

  public static BetterFabricConsolePreLaunch instance() {
    if (INSTANCE == null) {
      throw new IllegalStateException("BetterFabricConsolePreLaunch has not yet been initialized!");
    }
    return INSTANCE;
  }
}
