package org.chrisoft.jline4mcdsrv;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.commands.CommandSourceStack;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.jline.reader.Highlighter;
import org.jline.reader.LineReader;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

import java.util.regex.Pattern;

public final class MinecraftCommandHighlighter implements Highlighter {
    private final CommandDispatcher<CommandSourceStack> dispatcher;
    private final CommandSourceStack commandSourceStack;
    private final int[] colors;

    public MinecraftCommandHighlighter(final @NonNull CommandDispatcher<CommandSourceStack> dispatcher, final @NonNull CommandSourceStack commandSourceStack) {
        this.dispatcher = dispatcher;
        this.commandSourceStack = commandSourceStack;
        this.colors = JLineForMcDSrvMain.get().config().highlightColorIndices();
    }

    @Override
    public AttributedString highlight(final @NonNull LineReader reader, final @NonNull String buffer) {
        final AttributedStringBuilder sb = new AttributedStringBuilder();
        final ParseResults<CommandSourceStack> results = this.dispatcher.parse(buffer, this.commandSourceStack);
        int pos = 0;
        int component = -1;
        for (final ParsedCommandNode<CommandSourceStack> node : results.getContext().getNodes()) {
            if (node.getRange().getStart() >= buffer.length()) {
                break;
            }
            final int start = node.getRange().getStart();
            final int end = Math.min(node.getRange().getEnd(), buffer.length());
            if (node.getNode() instanceof LiteralCommandNode) {
                sb.append(buffer.substring(pos, start), AttributedStyle.DEFAULT);
                sb.append(buffer.substring(start, end), AttributedStyle.DEFAULT);
            } else {
                if (++component >= this.colors.length)
                    component = 0;
                sb.append(buffer.substring(pos, start), AttributedStyle.DEFAULT);
                sb.append(buffer.substring(start, end), AttributedStyle.DEFAULT.foreground(this.colors[component]));
            }
            pos = end;
        }
        if (pos < buffer.length()) {
            sb.append((buffer.substring(pos)), AttributedStyle.DEFAULT);
        }
        return sb.toAttributedString();
    }

    @Override
    public void setErrorPattern(final @NonNull Pattern errorPattern) {
    }

    @Override
    public void setErrorIndex(final int errorIndex) {
    }
}
