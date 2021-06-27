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
package xyz.jpenilla.betterfabricconsole;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.Message;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;

final class MinecraftCommandCompleter implements Completer {
  private final CommandDispatcher<CommandSourceStack> dispatcher;
  private final CommandSourceStack commandSourceStack;

  MinecraftCommandCompleter(final @NonNull CommandDispatcher<CommandSourceStack> dispatcher, final @NonNull CommandSourceStack commandSourceStack) {
    this.dispatcher = dispatcher;
    this.commandSourceStack = commandSourceStack;
  }

  @Override
  public void complete(final @NonNull LineReader reader, final @NonNull ParsedLine line, final @NonNull List<@NonNull Candidate> candidates) {
    final StringReader stringReader = Util.prepareStringReader(line.line());
    final ParseResults<CommandSourceStack> results = this.dispatcher.parse(stringReader, this.commandSourceStack);
    final CompletableFuture<Suggestions> suggestionsFuture = this.dispatcher.getCompletionSuggestions(results, line.cursor());
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
