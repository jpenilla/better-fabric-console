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
package xyz.jpenilla.betterfabricconsole.endermux;

import net.minecraft.server.dedicated.DedicatedServer;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.jspecify.annotations.NullMarked;
import xyz.jpenilla.betterfabricconsole.configuration.Config;
import xyz.jpenilla.betterfabricconsole.console.MinecraftCommandHighlighter;
import xyz.jpenilla.endermux.protocol.Payloads;
import xyz.jpenilla.endermux.server.api.ConsoleHooks;

@NullMarked
public final class FabricCommandHighlighter implements ConsoleHooks.CommandHighlighter {
  private final DedicatedServer server;
  private final Config.StyleColor[] colors;

  public FabricCommandHighlighter(final DedicatedServer server, final Config.StyleColor[] colors) {
    this.server = server;
    this.colors = colors;
  }

  @Override
  public Payloads.SyntaxHighlightResponse highlight(final String command) {
    return new Payloads.SyntaxHighlightResponse(command, this.highlightCommand(command));
  }

  private String highlightCommand(final String command) {
    try {
      return MinecraftCommandHighlighter.highlight(this.server, command, this.colors).toAnsi();
    } catch (final Exception e) {
      final AttributedStringBuilder builder = new AttributedStringBuilder();
      builder.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.RED));
      builder.append(command);
      return builder.toAnsi();
    }
  }
}
