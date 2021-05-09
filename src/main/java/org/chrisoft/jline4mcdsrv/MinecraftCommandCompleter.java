package org.chrisoft.jline4mcdsrv;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.Message;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import net.minecraft.commands.CommandSourceStack;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;

import java.util.List;
import java.util.concurrent.CompletableFuture;

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
