/*
 * This file is part of Better Fabric Console, licensed under the MIT License.
 *
 * Copyright (c) 2021 Jason Penilla
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
package xyz.jpenilla.betterfabricconsole.mixin;

import com.mojang.authlib.GameProfileRepository;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.datafixers.DataFixer;
import java.net.Proxy;
import java.util.UUID;
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
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.jpenilla.betterfabricconsole.BetterFabricConsole;
import xyz.jpenilla.betterfabricconsole.ConsoleThread;

@Mixin(DedicatedServer.class)
abstract class DedicatedServerMixin extends MinecraftServer {
  @Final @Shadow static Logger LOGGER;

  private final FabricServerAudiences audiences = FabricServerAudiences.of(this);
  private final LegacyComponentSerializer legacySerializer = LegacyComponentSerializer.builder()
    .flattener(this.audiences.flattener())
    .hexColors()
    .character(LegacyComponentSerializer.SECTION_CHAR)
    .hexCharacter(LegacyComponentSerializer.HEX_CHAR)
    .build();

  DedicatedServerMixin(final Thread thread, final RegistryAccess.RegistryHolder registryHolder, final LevelStorageSource.LevelStorageAccess levelStorageAccess, final WorldData worldData, final PackRepository packRepository, final Proxy proxy, final DataFixer dataFixer, final ServerResources serverResources, final MinecraftSessionService minecraftSessionService, final GameProfileRepository gameProfileRepository, final GameProfileCache gameProfileCache, final ChunkProgressListenerFactory chunkProgressListenerFactory) {
    super(thread, registryHolder, levelStorageAccess, worldData, packRepository, proxy, dataFixer, serverResources, minecraftSessionService, gameProfileRepository, gameProfileCache, chunkProgressListenerFactory);
  }

  @Inject(method = "initServer", at = @At(value = "HEAD"))
  private void injectInitServer(final @NonNull CallbackInfoReturnable<Boolean> info) {
    BetterFabricConsole.LOGGER.info("Initializing Better Fabric Console console thread...");
    final ConsoleThread consoleThread = new ConsoleThread((DedicatedServer) (Object) this);
    consoleThread.setDaemon(true);
    consoleThread.setUncaughtExceptionHandler(new DefaultUncaughtExceptionHandler(LOGGER));
    consoleThread.init();
    consoleThread.start();
  }

  @Override
  public void sendMessage(final @NonNull Component component, final @NonNull UUID identity) {
    LOGGER.info(this.legacySerializer.serialize(this.audiences.toAdventure(component)));
  }
}
