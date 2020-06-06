package org.chrisoft.jline4mcdsrv;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.context.ParsedCommandNode;
import net.minecraft.server.command.ServerCommandSource;
import org.jline.reader.Highlighter;
import org.jline.reader.LineReader;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

import java.util.List;
import java.util.regex.Pattern;

public class MinecraftCommandHighlighter implements Highlighter
{
    private CommandDispatcher<ServerCommandSource> cmddispatcher;
    private ServerCommandSource cmdsrc;

    public MinecraftCommandHighlighter(CommandDispatcher<ServerCommandSource> _cmddispatcher, ServerCommandSource _cmdsrc)
    {
        cmddispatcher = _cmddispatcher;
        cmdsrc = _cmdsrc;
    }

    @Override
    public AttributedString highlight(LineReader reader, String buffer)
    {
        final int[] colors = new int[]{AttributedStyle.CYAN, AttributedStyle.YELLOW, AttributedStyle.GREEN, AttributedStyle.MAGENTA, AttributedStyle.WHITE};
        AttributedStringBuilder sb = new AttributedStringBuilder();
        ParseResults<ServerCommandSource> parse = cmddispatcher.parse(buffer, cmdsrc);
        List nodes = parse.getContext().getNodes();
        int pos = 0;
        int component = -1;
        for (Object _node : nodes) {
            ParsedCommandNode<ServerCommandSource> pcn = (ParsedCommandNode)_node;
            if (++component >= colors.length)
                component = 0 ;
            if (pcn.getRange().getStart() >= buffer.length())
                break;
            int start = pcn.getRange().getStart();
            int end = pcn.getRange().getEnd();
            sb.append(buffer.substring(pos, start), AttributedStyle.DEFAULT);
            sb.append(buffer.substring(start, end), AttributedStyle.DEFAULT.foreground(colors[component]));
            pos = end;
        }
        if (pos < buffer.length()) {
            sb.append((buffer.substring(pos)), AttributedStyle.DEFAULT);
        }
        return sb.toAttributedString();
    }

    @Override
    public void setErrorPattern(Pattern errorPattern)
    {
    }

    @Override
    public void setErrorIndex(int errorIndex)
    {
    }
}
