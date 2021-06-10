package org.chrisoft.jline4mcdsrv;

import ca.stellardrift.confabricate.Confabricate;
import io.papermc.paper.console.HexFormattingConverter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.plugins.util.PluginRegistry;
import org.apache.logging.log4j.core.config.plugins.util.PluginType;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.reference.ValueReference;

public final class JLineForMcDSrv implements ModInitializer {
    public static final Logger LOGGER = LogManager.getLogger("jline4mcdsrv");
    private static JLineForMcDSrv instance;
    private Config config;
    private ModContainer modContainer;

    public JLineForMcDSrv() {
        try {
            loadPluginsFromClassLoader(HexFormattingConverter.class.getClassLoader());
        } catch (final ReflectiveOperationException e) {
            LOGGER.error("Failed to load extra Log4j2 plugins", e);
        }
    }

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
                for (PluginType<?> pl : discoveredPlugins) {
                    if (!forCategory.contains(pl)) {
                        forCategory.add(pl);
                    }
                }
            }
        });
    }

    public static JLineForMcDSrv get() {
        if (instance == null) {
            throw new IllegalStateException("jline4mcdsrv has not yet been initialized!");
        }
        return instance;
    }

    @Override
    public void onInitialize() {
        instance = this;
        this.modContainer = FabricLoader.getInstance().getModContainer("jline4mcdsrv")
                .orElseThrow(() -> new IllegalStateException("Could not find mod container for jline4mcdsrv"));
        this.loadModConfig();
    }

    private void loadModConfig() {
        try {
            final ValueReference<Config, CommentedConfigurationNode> reference = Confabricate.configurationFor(this.modContainer, false).referenceTo(Config.class);
            this.config = reference.get();
            reference.setAndSave(this.config);
        } catch (final ConfigurateException ex) {
            throw new RuntimeException("Failed to load config", ex);
        }
    }

    public Config config() {
        return this.config;
    }
}
