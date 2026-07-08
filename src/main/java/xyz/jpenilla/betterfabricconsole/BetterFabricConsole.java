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
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.kyori.adventure.platform.modcommon.MinecraftServerAudiences;
import net.minecraft.DefaultUncaughtExceptionHandler;
import net.minecraft.server.dedicated.DedicatedServer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import xyz.jpenilla.betterfabricconsole.command.ModCommands;
import xyz.jpenilla.betterfabricconsole.configuration.Config;
import xyz.jpenilla.betterfabricconsole.console.ConsoleState;
import xyz.jpenilla.betterfabricconsole.console.ConsoleThread;
import xyz.jpenilla.betterfabricconsole.console.MinecraftCommandCompleter;
import xyz.jpenilla.betterfabricconsole.console.MinecraftCommandHighlighter;
import xyz.jpenilla.betterfabricconsole.console.MinecraftConsoleParser;

@NullMarked
public final class BetterFabricConsole implements ModInitializer {
  public static final Logger LOGGER = LogUtils.getLogger();
  private static @Nullable BetterFabricConsole INSTANCE;

  @Override
  public void onInitialize() {
    INSTANCE = this;
    CommandRegistrationCallback.EVENT.register(ModCommands::register);
    ServerLifecycleEvents.SERVER_STARTING.register(server -> this.initConsoleThread((DedicatedServer) server));
  }

  private void initConsoleThread(final DedicatedServer server) {
    final ConsoleState consoleState = BetterFabricConsolePreLaunch.instance().consoleState();
    consoleState.completer().delegateTo(new MinecraftCommandCompleter(server, MinecraftServerAudiences.of(server)));
    consoleState.highlighter().delegateTo(new MinecraftCommandHighlighter(server, this.config().highlightColors()));
    consoleState.parser().delegateTo(new MinecraftConsoleParser(server));
    final ConsoleThread consoleThread = new ConsoleThread(server, consoleState.lineReader());
    consoleThread.setDaemon(true);
    consoleThread.setUncaughtExceptionHandler(new DefaultUncaughtExceptionHandler(LOGGER));
    consoleThread.start();
  }

  public Config config() {
    return BetterFabricConsolePreLaunch.instance().config();
  }

  public static BetterFabricConsole instance() {
    if (INSTANCE == null) {
      throw new IllegalStateException("Better Fabric Console has not yet been initialized!");
    }
    return INSTANCE;
  }
}
