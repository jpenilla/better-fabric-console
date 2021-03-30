package org.chrisoft.jline4mcdsrv.mixin;

import com.mojang.authlib.GameProfileRepository;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.datafixers.DataFixer;
import net.kyori.adventure.platform.fabric.FabricServerAudiences;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.minecraft.DefaultUncaughtExceptionHandler;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerResources;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.progress.ChunkProgressListenerFactory;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.WorldData;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.chrisoft.jline4mcdsrv.JLineConsoleThread;
import org.chrisoft.jline4mcdsrv.JLineForMcDSrvMain;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.Proxy;
import java.util.UUID;

@Mixin(DedicatedServer.class)
abstract class DedicatedServerMixin extends MinecraftServer {
    @Final @Shadow private static Logger LOGGER;

    private final FabricServerAudiences audiences = FabricServerAudiences.of(this);
    private final LegacyComponentSerializer serializer = LegacyComponentSerializer.builder()
            .flattener(this.audiences.flattener())
            .hexColors()
            .character(LegacyComponentSerializer.SECTION_CHAR)
            .hexCharacter(LegacyComponentSerializer.HEX_CHAR)
            .build();

    public DedicatedServerMixin(Thread thread, RegistryAccess.RegistryHolder registryHolder, LevelStorageSource.LevelStorageAccess levelStorageAccess, WorldData worldData, PackRepository packRepository, Proxy proxy, DataFixer dataFixer, ServerResources serverResources, MinecraftSessionService minecraftSessionService, GameProfileRepository gameProfileRepository, GameProfileCache gameProfileCache, ChunkProgressListenerFactory chunkProgressListenerFactory) {
        super(thread, registryHolder, levelStorageAccess, worldData, packRepository, proxy, dataFixer, serverResources, minecraftSessionService, gameProfileRepository, gameProfileCache, chunkProgressListenerFactory);
    }

    @Inject(method = "initServer", at = @At(value = "INVOKE", target = "Lorg/apache/logging/log4j/Logger;info(Ljava/lang/String;)V", shift = At.Shift.BEFORE, ordinal = 0))
    private void injectInitServer(final @NonNull CallbackInfoReturnable<Boolean> info) {
        final JLineConsoleThread consoleThread = new JLineConsoleThread((DedicatedServer) (Object) this);
        consoleThread.setDaemon(true);
        consoleThread.setUncaughtExceptionHandler(new DefaultUncaughtExceptionHandler(LOGGER));
        consoleThread.start();
        JLineForMcDSrvMain.LOGGER.info("Finished initializing jline4mcdsrv console thread.");
    }

    @Override
    public void sendMessage(final Component component, final UUID identity) {
        LOGGER.info(this.serializer.serialize(this.audiences.toAdventure(component)));
    }
}
