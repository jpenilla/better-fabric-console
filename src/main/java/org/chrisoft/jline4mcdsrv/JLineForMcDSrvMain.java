package org.chrisoft.jline4mcdsrv;

import ca.stellardrift.confabricate.Confabricate;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.reference.ConfigurationReference;

public final class JLineForMcDSrvMain implements ModInitializer {
    public static final Logger LOGGER = LogManager.getLogger("jline4mcdsrv");
    private static JLineForMcDSrvMain instance;
    private Config config;

    public static JLineForMcDSrvMain get() {
        if (instance == null) {
            throw new IllegalStateException("jline4mcdsrv has not yet been initialized!");
        }
        return instance;
    }

    @Override
    public void onInitialize() {
        instance = this;
        final ModContainer container = FabricLoader.getInstance().getModContainer("jline4mcdsrv")
                .orElseThrow(() -> new IllegalStateException("Could not find mod container for jline4mcdsrv"));
        try {
            final ConfigurationReference<CommentedConfigurationNode> reference = Confabricate.configurationFor(container, false);
            reference.load();
            this.config = reference.node().get(Config.class);
            reference.save(reference.loader().createNode().set(this.config));
        } catch (final ConfigurateException ex) {
            throw new RuntimeException("Failed to load config", ex);
        }
    }

    public Config config() {
        return this.config;
    }
}
