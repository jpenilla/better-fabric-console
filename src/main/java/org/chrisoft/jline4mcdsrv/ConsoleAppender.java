package org.chrisoft.jline4mcdsrv;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.appender.rewrite.RewritePolicy;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.jline.reader.LineReader;

import java.lang.reflect.Field;
import java.util.Map;

final class ConsoleAppender extends AbstractAppender {

    private final LineReader lineReader;
    private RewritePolicy rewritePolicy;
    private Map<String, String> mappings;

    public ConsoleAppender(final @NonNull LineReader lineReader) {
        super("Console", null, PatternLayout.newBuilder().withPattern(JLineForMcDSrvMain.get().config().logPattern()).build(), false);
        this.lineReader = lineReader;
    }

    @SuppressWarnings("unchecked")
    public void installNotEnoughCrashesRewritePolicy(final @NonNull RewritePolicy policy) {
        this.rewritePolicy = policy;
        try {
            final Class<?> clazz = Class.forName("fudge.notenoughcrashes.fabric.StacktraceDeobfuscator");
            final Field mappingsField = clazz.getDeclaredField("mappings");
            mappingsField.setAccessible(true);
            this.mappings = (Map<String, String>) mappingsField.get(null);
        } catch (final ClassNotFoundException | NoSuchFieldException | IllegalAccessException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void append(@NonNull LogEvent event) {
        if (this.rewritePolicy != null && this.mappings != null) { // NEC compat
            event = this.rewritePolicy.rewrite(event);
            event = Log4jLogEvent.newBuilder()
                    .setLoggerName(this.mappings.getOrDefault(event.getLoggerName(), event.getLoggerName()))
                    .setMarker(event.getMarker())
                    .setLoggerFqcn(this.mappings.getOrDefault(event.getLoggerFqcn(), event.getLoggerFqcn()))
                    .setLevel(event.getLevel())
                    .setMessage(event.getMessage())
                    .setThrown(event.getThrown())
                    .setContextMap(event.getContextMap())
                    .setContextStack(event.getContextStack())
                    .setThreadName(event.getThreadName())
                    .setSource(event.getSource())
                    .setTimeMillis(event.getTimeMillis())
                    .build();
        }

        if (this.lineReader.isReading())
            this.lineReader.callWidget(this.lineReader.CLEAR);

        this.lineReader.getTerminal().writer().print(getLayout().toSerializable(event).toString());

        if (this.lineReader.isReading()) {
            this.lineReader.callWidget(this.lineReader.REDRAW_LINE);
            this.lineReader.callWidget(this.lineReader.REDISPLAY);
        }
        this.lineReader.getTerminal().writer().flush();
    }
}
