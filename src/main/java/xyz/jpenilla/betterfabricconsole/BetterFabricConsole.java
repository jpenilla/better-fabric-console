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

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.kyori.adventure.text.format.TextColor;
import net.minecraft.DefaultUncaughtExceptionHandler;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.dedicated.DedicatedServer;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.framework.qual.DefaultQualifier;
import org.slf4j.Logger;
import xyz.jpenilla.betterfabricconsole.configuration.Config;
import xyz.jpenilla.betterfabricconsole.console.ConsoleState;
import xyz.jpenilla.betterfabricconsole.console.ConsoleThread;
import xyz.jpenilla.betterfabricconsole.console.MinecraftCommandCompleter;
import xyz.jpenilla.betterfabricconsole.console.MinecraftCommandHighlighter;

import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.GRAY;
import static net.kyori.adventure.text.format.TextColor.color;
import static net.kyori.adventure.text.format.TextDecoration.BOLD;
import static net.kyori.adventure.text.format.TextDecoration.ITALIC;
import static net.minecraft.commands.Commands.literal;

@DefaultQualifier(NonNull.class)
public final class BetterFabricConsole implements ModInitializer {
  public static final Logger LOGGER = LogUtils.getLogger();
  private static final TextColor PINK = color(0xFF79C6);
  private static @MonotonicNonNull BetterFabricConsole INSTANCE;

  @Override
  public void onInitialize() {
    INSTANCE = this;
    CommandRegistrationCallback.EVENT.register(this::registerCommands);
    ServerLifecycleEvents.SERVER_STARTING.register(server -> this.initConsoleThread((DedicatedServer) server));
  }

  private void initConsoleThread(final DedicatedServer server) {
    final ConsoleState consoleState = BetterFabricConsolePreLaunch.INSTANCE.consoleState;
    consoleState.completer().delegateTo(new MinecraftCommandCompleter(server));
    consoleState.highlighter().delegateTo(new MinecraftCommandHighlighter(server, this.config().highlightColors()));
    final ConsoleThread consoleThread = new ConsoleThread(server, consoleState.lineReader());
    consoleThread.setDaemon(true);
    consoleThread.setUncaughtExceptionHandler(new DefaultUncaughtExceptionHandler(LOGGER));
    consoleThread.start();
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

  private int executeCommand(final CommandContext<CommandSourceStack> ctx) {
    ctx.getSource().sendMessage(text()
      .color(GRAY)
      .append(text("Better Fabric Console", PINK, BOLD))
      .append(text().content(" v").decorate(ITALIC))
      .append(text(BetterFabricConsolePreLaunch.INSTANCE.modContainer.getMetadata().getVersion().getFriendlyString())));
    return Command.SINGLE_SUCCESS;
  }

  public Config config() {
    return BetterFabricConsolePreLaunch.INSTANCE.config;
  }

  public static BetterFabricConsole instance() {
    if (INSTANCE == null) {
      throw new IllegalStateException("Better Fabric Console has not yet been initialized!");
    }
    return INSTANCE;
  }
}
