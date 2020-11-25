package org.chrisoft.jline4mcdsrv;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import net.minecraft.server.command.ServerCommandSource;
import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class MinecraftCommandCompleter implements Completer
{
    private final CommandDispatcher<ServerCommandSource> cmdDispatcher;
    private final ServerCommandSource cmdSrc;

    public MinecraftCommandCompleter(CommandDispatcher<ServerCommandSource> cmdDispatcher, ServerCommandSource cmdSrc)
    {
        this.cmdDispatcher = cmdDispatcher;
        this.cmdSrc = cmdSrc;
    }

    @Override
    public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates)
    {
        ParseResults<ServerCommandSource> parseRes = cmdDispatcher.parse(line.line(), cmdSrc);
        CompletableFuture<Suggestions> cs = cmdDispatcher.getCompletionSuggestions(parseRes, line.cursor());
        Suggestions sl = cs.join();
        for (Suggestion s : sl.getList()) {
            String applied = s.apply(line.line());
            ParsedLine apl = reader.getParser().parse(applied, line.cursor());
            String candStr = apl.word();
            candidates.add(new Candidate(candStr));
        }
    }
}
