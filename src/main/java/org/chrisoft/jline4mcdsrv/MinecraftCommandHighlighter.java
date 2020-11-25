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

import java.util.regex.Pattern;

public class MinecraftCommandHighlighter implements Highlighter
{
    private final CommandDispatcher<ServerCommandSource> cmdDispatcher;
    private final ServerCommandSource cmdSrc;
    private final int[] colors;

    public MinecraftCommandHighlighter(CommandDispatcher<ServerCommandSource> cmdDispatcher, ServerCommandSource cmdSrc)
    {
        this.cmdDispatcher = cmdDispatcher;
        this.cmdSrc = cmdSrc;
        colors = JLineForMcDSrvMain.config.getHighlightColors();
    }

    @Override
    public AttributedString highlight(LineReader reader, String buffer)
    {
        AttributedStringBuilder sb = new AttributedStringBuilder();
        ParseResults<ServerCommandSource> parse = cmdDispatcher.parse(buffer, cmdSrc);
        int pos = 0;
        int component = -1;
        for (ParsedCommandNode<ServerCommandSource> pcn : parse.getContext().getNodes()) {
            if (++component >= colors.length)
                component = 0;
            if (pcn.getRange().getStart() >= buffer.length())
                break;
            int start = pcn.getRange().getStart();
            int end = Math.min(pcn.getRange().getEnd(), buffer.length());
            sb.append(buffer.substring(pos, start), AttributedStyle.DEFAULT);
            sb.append(buffer.substring(start, end), AttributedStyle.DEFAULT.foreground(colors[component]));
            pos = end;
        }
        if (pos < buffer.length())
            sb.append((buffer.substring(pos)), AttributedStyle.DEFAULT);
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
