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
package xyz.jpenilla.betterfabricconsole.console;

import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import java.util.regex.Pattern;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import org.jline.reader.Highlighter;
import org.jline.reader.LineReader;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.jspecify.annotations.NullMarked;
import xyz.jpenilla.betterfabricconsole.configuration.Config;
import xyz.jpenilla.betterfabricconsole.util.Util;

@NullMarked
public record MinecraftCommandHighlighter(
  MinecraftServer server,
  Config.StyleColor[] colors
) implements Highlighter {
  @Override
  public AttributedString highlight(final LineReader reader, final String buffer) {
    return highlight(this.server, buffer, this.colors);
  }

  @Override
  public void setErrorPattern(final Pattern errorPattern) {
  }

  @Override
  public void setErrorIndex(final int errorIndex) {
  }

  public static AttributedString highlight(
    final MinecraftServer server,
    final String buffer,
    final Config.StyleColor[] colors
  ) {
    final AttributedStringBuilder builder = new AttributedStringBuilder();
    final StringReader stringReader = Util.prepareStringReader(buffer);
    final ParseResults<CommandSourceStack> results = server.getCommands().getDispatcher().parse(stringReader, server.createCommandSourceStack());
    int pos = 0;
    if (buffer.startsWith("/")) {
      builder.append("/", AttributedStyle.DEFAULT);
      pos = 1;
    }

    int colorIndex = -1;
    for (final ParsedCommandNode<CommandSourceStack> node : results.getContext().getLastChild().getNodes()) {
      if (node.getRange().getStart() >= buffer.length()) {
        break;
      }

      final int start = node.getRange().getStart();
      final int end = Math.min(node.getRange().getEnd(), buffer.length());
      if (node.getNode() instanceof LiteralCommandNode) {
        builder.append(buffer.substring(pos, start), AttributedStyle.DEFAULT);
        builder.append(buffer.substring(start, end), AttributedStyle.DEFAULT);
      } else {
        if (++colorIndex >= colors.length) {
          colorIndex = 0;
        }
        builder.append(buffer.substring(pos, start), AttributedStyle.DEFAULT);
        builder.append(buffer.substring(start, end), AttributedStyle.DEFAULT.foreground(colors[colorIndex].index()));
      }
      pos = end;
    }

    if (pos < buffer.length()) {
      builder.append(buffer.substring(pos), AttributedStyle.DEFAULT.foreground(AttributedStyle.RED));
    }

    return builder.toAttributedString();
  }
}
