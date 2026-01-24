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

import com.mojang.brigadier.Message;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.platform.modcommon.MinecraftServerAudiences;
import net.kyori.adventure.text.serializer.ansi.ANSIComponentSerializer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.server.MinecraftServer;
import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import xyz.jpenilla.betterfabricconsole.util.Util;
import xyz.jpenilla.endermux.jline.MinecraftCandidate;

@NullMarked
public record MinecraftCommandCompleter(MinecraftServer server, MinecraftServerAudiences audiences) implements Completer {
  @Override
  public void complete(final LineReader reader, final ParsedLine line, final List<Candidate> candidates) {
    final StringReader stringReader = Util.prepareStringReader(line.line());
    final ParseResults<CommandSourceStack> results = this.server.getCommands().getDispatcher().parse(stringReader, this.server.createCommandSourceStack());
    final CompletableFuture<Suggestions> suggestionsFuture = this.server.getCommands().getDispatcher().getCompletionSuggestions(results, line.cursor());
    final Suggestions suggestions = suggestionsFuture.join();

    final ParseContext parseContext = new ParseContext(line.line(), results.getContext().findSuggestionContext(line.cursor()).startPos);
    for (final Suggestion suggestion : suggestions.getList()) {
      final String suggestionText = suggestion.getText();
      if (suggestionText.isEmpty()) {
        continue;
      }

      candidates.add(this.toCandidate(suggestion, parseContext));
    }
  }

  private Candidate toCandidate(final Suggestion suggestion, final ParseContext context) {
    return this.toCandidate(
      context.line.substring(context.suggestionStart, suggestion.getRange().getStart()) + suggestion.getText(),
      suggestion.getTooltip()
    );
  }

  private Candidate toCandidate(final String suggestionText, final Message descriptionMessage) {
    final @Nullable String description = Optional.ofNullable(descriptionMessage)
      .map(tooltip -> {
        final Component tooltipComponent = ComponentUtils.fromMessage(tooltip);
        return tooltipComponent.equals(Component.empty()) ? null : this.audiences.asAdventure(tooltipComponent);
      })
      .map(adventure -> ANSIComponentSerializer.ansi().serialize(adventure))
      .orElse(null);
    //noinspection SpellCheckingInspection
    return new MinecraftCandidate(
      suggestionText,
      suggestionText,
      null,
      description,
      null,
      null,
      /*
      in an ideal world, this would sometimes be true if the suggestion represented the final possible value for a word.
      Like for `/execute alig`, pressing enter on align would add a trailing space if this value was true. But not all
      suggestions should add spaces after, like `/execute as @`, accepting any suggestion here would be valid, but its also
      valid to have a `[` following the selector
       */
      false
    );
  }

  private record ParseContext(String line, int suggestionStart) {
  }
}
