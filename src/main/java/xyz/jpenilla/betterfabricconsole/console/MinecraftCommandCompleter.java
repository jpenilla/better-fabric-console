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
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.serializer.ansi.ANSIComponentSerializer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.server.MinecraftServer;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.framework.qual.DefaultQualifier;
import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;
import xyz.jpenilla.betterfabricconsole.util.Util;

@DefaultQualifier(NonNull.class)
public record MinecraftCommandCompleter(MinecraftServer server) implements Completer {
  @Override
  public void complete(final LineReader reader, final ParsedLine line, final List<Candidate> candidates) {
    final StringReader stringReader = Util.prepareStringReader(line.line());
    final ParseResults<CommandSourceStack> results = this.server.getCommands().getDispatcher().parse(stringReader, this.server.createCommandSourceStack());
    final CompletableFuture<Suggestions> suggestionsFuture = this.server.getCommands().getDispatcher().getCompletionSuggestions(results, line.cursor());
    final Suggestions suggestions = suggestionsFuture.join();

    for (final Suggestion suggestion : suggestions.getList()) {
      final String suggestionText = suggestion.getText();
      if (suggestionText.isEmpty()) {
        continue;
      }

      final @Nullable String description = Optional.ofNullable(suggestion.getTooltip())
        .map(tooltip -> {
          final Component tooltipComponent = ComponentUtils.fromMessage(tooltip);
          return tooltipComponent.equals(Component.empty()) ? null : tooltipComponent.asComponent();
        })
        .map(adventure -> ANSIComponentSerializer.ansi().serialize(adventure))
        .orElse(null);

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
