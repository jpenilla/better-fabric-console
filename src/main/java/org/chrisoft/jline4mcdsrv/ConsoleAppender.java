package org.chrisoft.jline4mcdsrv;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.jline.reader.LineReader;

final class ConsoleAppender extends AbstractAppender {
    private final LineReader lineReader;
    private NotEnoughCrashesRewriter rewriter = null;

    public ConsoleAppender(final @NonNull LineReader lineReader) {
        super(
                "Console",
                null,
                PatternLayout.newBuilder().withPattern(JLineForMcDSrv.get().config().logPattern()).build(),
                false,
                new Property[0]
        );
        this.lineReader = lineReader;
    }

    public void setRewriter(final @Nullable NotEnoughCrashesRewriter rewriter) {
        this.rewriter = rewriter;
    }

    private @NonNull LogEvent rewrite(final @NonNull LogEvent event) {
        if (this.rewriter == null) {
            return event;
        }
        return this.rewriter.rewrite(event);
    }

    @Override
    public void append(@NonNull LogEvent event) {
        if (this.lineReader.isReading()) {
            this.lineReader.callWidget(this.lineReader.CLEAR);
        }

        this.lineReader.getTerminal().writer().print(this.getLayout().toSerializable(this.rewrite(event)).toString());

        if (this.lineReader.isReading()) {
            this.lineReader.callWidget(this.lineReader.REDRAW_LINE);
            this.lineReader.callWidget(this.lineReader.REDISPLAY);
        }
        this.lineReader.getTerminal().writer().flush();
    }
}
