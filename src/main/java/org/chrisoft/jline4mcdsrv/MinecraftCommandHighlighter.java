package org.chrisoft.jline4mcdsrv;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
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

final class MinecraftCommandHighlighter implements Highlighter {
    private final CommandDispatcher<CommandSourceStack> dispatcher;
    private final CommandSourceStack commandSourceStack;
    private final Config.StyleColor[] colors;

    public MinecraftCommandHighlighter(final @NonNull CommandDispatcher<CommandSourceStack> dispatcher, final @NonNull CommandSourceStack commandSourceStack) {
        this.dispatcher = dispatcher;
        this.commandSourceStack = commandSourceStack;
        this.colors = JLineForMcDSrvMain.get().config().highlightColors();
    }

    @Override
    public AttributedString highlight(final @NonNull LineReader reader, final @NonNull String buffer) {
        final AttributedStringBuilder builder = new AttributedStringBuilder();
        final StringReader stringReader = new StringReader(buffer);
        if (stringReader.canRead() && stringReader.peek() == '/') {
            stringReader.skip();
        }
        final ParseResults<CommandSourceStack> results = this.dispatcher.parse(stringReader, this.commandSourceStack);
        int pos = 0;
        if (buffer.startsWith("/")) {
            builder.append("/", AttributedStyle.DEFAULT);
            pos = 1;
        }
        int component = -1;
        for (final ParsedCommandNode<CommandSourceStack> node : results.getContext().getNodes()) {
            if (node.getRange().getStart() >= buffer.length()) {
                break;
            }
            final int start = node.getRange().getStart();
            final int end = Math.min(node.getRange().getEnd(), buffer.length());
            if (node.getNode() instanceof LiteralCommandNode) {
                builder.append(buffer.substring(pos, start), AttributedStyle.DEFAULT);
                builder.append(buffer.substring(start, end), AttributedStyle.DEFAULT);
            } else {
                if (++component >= this.colors.length) {
                    component = 0;
                }
                builder.append(buffer.substring(pos, start), AttributedStyle.DEFAULT);
                builder.append(buffer.substring(start, end), AttributedStyle.DEFAULT.foreground(this.colors[component].index()));
            }
            pos = end;
        }
        if (pos < buffer.length()) {
            builder.append((buffer.substring(pos)), AttributedStyle.DEFAULT.foreground(AttributedStyle.RED));
        }
        return builder.toAttributedString();
    }

    @Override
    public void setErrorPattern(final @NonNull Pattern errorPattern) {
    }

    @Override
    public void setErrorIndex(final int errorIndex) {
    }
}
