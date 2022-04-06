/*
 * This file is part of Better Fabric Console, licensed under the MIT License.
 *
 * Copyright (c) 2021-2022 Jason Penilla
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

import com.mojang.brigadier.Message;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;
import xyz.jpenilla.betterfabricconsole.util.Util;

final class MinecraftCommandCompleter implements Completer {
  private final Supplier<@Nullable ? extends MinecraftServer> server;

  MinecraftCommandCompleter(final Supplier<@Nullable ? extends MinecraftServer> server) {
    this.server = server;
  }

  @Override
  public void complete(final @NonNull LineReader reader, final @NonNull ParsedLine line, final @NonNull List<@NonNull Candidate> candidates) {
    final @Nullable MinecraftServer server = this.server.get();
    if (server == null) {
      return;
    }
    final StringReader stringReader = Util.prepareStringReader(line.line());
    final ParseResults<CommandSourceStack> results = server.getCommands().getDispatcher().parse(stringReader, server.createCommandSourceStack());
    final CompletableFuture<Suggestions> suggestionsFuture = server.getCommands().getDispatcher().getCompletionSuggestions(results, line.cursor());
    final Suggestions suggestions = suggestionsFuture.join();

    for (final Suggestion suggestion : suggestions.getList()) {
      final String suggestionText = suggestion.getText();
      if (suggestionText.isEmpty()) {
        continue;
      }

      final Message suggestionTooltip = suggestion.getTooltip();
      final String description;
      if (suggestionTooltip == null) {
        description = null;
      } else {
        final String tooltipString = suggestionTooltip.getString();
        description = tooltipString.isEmpty() ? null : tooltipString;
      }

      candidates.add(new Candidate(
        suggestionText,
        suggestionText,
        null,
        description,
        null,
        null,
        false
      ));
    }
  }
}
