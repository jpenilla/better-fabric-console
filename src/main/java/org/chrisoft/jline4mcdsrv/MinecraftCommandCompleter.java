package org.chrisoft.jline4mcdsrv;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
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

public final class MinecraftCommandCompleter implements Completer {
    private final CommandDispatcher<CommandSourceStack> dispatcher;
    private final CommandSourceStack commandSourceStack;

    public MinecraftCommandCompleter(final @NonNull CommandDispatcher<CommandSourceStack> dispatcher, final @NonNull CommandSourceStack commandSourceStack) {
        this.dispatcher = dispatcher;
        this.commandSourceStack = commandSourceStack;
    }

    @Override
    public void complete(final @NonNull LineReader reader, final @NonNull ParsedLine line, final @NonNull List<@NonNull Candidate> candidates) {
        final ParseResults<CommandSourceStack> results = dispatcher.parse(line.line(), commandSourceStack);
        final CompletableFuture<Suggestions> suggestionsFuture = dispatcher.getCompletionSuggestions(results, line.cursor());
        final Suggestions suggestions = suggestionsFuture.join();
        for (final Suggestion suggestion : suggestions.getList()) {
            final String applied = suggestion.apply(line.line());
            final ParsedLine apl = reader.getParser().parse(applied, line.cursor());
            final String value = apl.word();
            candidates.add(new Candidate(value, value, null, null, null, null, false));
        }
    }
}
