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

    private CommandDispatcher<ServerCommandSource> cmddispatcher;
    private ServerCommandSource cmdsrc;
    private ParseResults<ServerCommandSource> parseres;

    public MinecraftCommandCompleter(CommandDispatcher<ServerCommandSource> _cmddispatcher, ServerCommandSource _cmdsrc)
    {
        cmddispatcher = _cmddispatcher;
        cmdsrc = _cmdsrc;
    }

    @Override
    public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates)
    {
        parseres = cmddispatcher.parse(line.line(), cmdsrc);
        CompletableFuture<Suggestions> cs = cmddispatcher.getCompletionSuggestions(parseres, line.cursor());
        Suggestions sl = cs.join();
        for (Suggestion s : sl.getList()) {
            String applied = s.apply(line.line());
            ParsedLine apl = reader.getParser().parse(applied, line.cursor());
            String candstr = apl.word();
            candidates.add(new Candidate(candstr));
        }
    }
}
