/*
 * This file is part of Better Fabric Console, licensed under the MIT License.
 *
 * Copyright (c) 2026 Jason Penilla
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
package xyz.jpenilla.betterfabricconsole.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import java.io.IOException;
import net.kyori.adventure.text.format.TextColor;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import org.jspecify.annotations.NullMarked;
import xyz.jpenilla.betterfabricconsole.BetterFabricConsole;
import xyz.jpenilla.betterfabricconsole.BetterFabricConsolePreLaunch;

import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.GRAY;
import static net.kyori.adventure.text.format.NamedTextColor.RED;
import static net.kyori.adventure.text.format.TextColor.color;
import static net.kyori.adventure.text.format.TextDecoration.BOLD;
import static net.kyori.adventure.text.format.TextDecoration.ITALIC;
import static net.minecraft.commands.Commands.literal;

@NullMarked
public final class ModCommands {
  private static final TextColor PINK = color(0xFF79C6);

  private ModCommands() {
  }

  @SuppressWarnings("unused")
  public static void register(
    final CommandDispatcher<CommandSourceStack> dispatcher,
    final CommandBuildContext commandBuildContext,
    final Commands.CommandSelection commandSelection
  ) {
    dispatcher.register(literal("better-fabric-console")
      .requires(Commands.hasPermission(Commands.LEVEL_OWNERS))
      .executes(ModCommands::executeVersion)
      .then(literal("dismiss-log4j-config-update")
        .executes(ModCommands::executeDismissLog4jConfigUpdate)));
  }

  private static int executeVersion(final CommandContext<CommandSourceStack> ctx) {
    ctx.getSource().sendMessage(text()
      .color(GRAY)
      .append(text("Better Fabric Console", PINK, BOLD))
      .append(text().content(" v").decorate(ITALIC))
      .append(text(BetterFabricConsolePreLaunch.instance().modContainer().getMetadata().getVersion().getFriendlyString())));
    return Command.SINGLE_SUCCESS;
  }

  private static int executeDismissLog4jConfigUpdate(final CommandContext<CommandSourceStack> ctx) {
    try {
      BetterFabricConsolePreLaunch.instance().log4jConfigManager().dismissUpdate();
    } catch (final IOException ex) {
      BetterFabricConsole.LOGGER.warn("Failed to dismiss Better Fabric Console Log4j config update", ex);
      ctx.getSource().sendFailure(text("Failed to dismiss Log4j config update. Check the server log for details.", RED));
      return 0;
    }

    ctx.getSource().sendMessage(text("Log4j config update dismissed.", GRAY));
    ctx.getSource().sendMessage(text("The current bundled Log4j default is now marked as reviewed.", GRAY));
    ctx.getSource().sendMessage(text(
      "This only updates Better Fabric Console's tracking metadata; it does not edit your active log4j2.xml.",
      GRAY
    ));
    return Command.SINGLE_SUCCESS;
  }
}
