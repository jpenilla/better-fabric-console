package org.chrisoft.jline4mcdsrv;

import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.rewrite.RewriteAppender;
import org.apache.logging.log4j.core.appender.rewrite.RewritePolicy;
import org.apache.logging.log4j.core.config.AppenderRef;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class NotEnoughCrashesRewriter {
    private static final Class<?> STACKTRACE_DEOBFUSCATOR_CLASS = findClass("fudge.notenoughcrashes.fabric.StacktraceDeobfuscator");

    private final RewritePolicy rewritePolicy;
    private Map<String, String> mappings;

    private NotEnoughCrashesRewriter(final @NonNull RewritePolicy rewritePolicy, final @NonNull LoggerContext loggerContext, final @NonNull LoggerConfig loggerConfig) {
        this.rewritePolicy = rewritePolicy;
        this.loadMappings();
        this.removeSysOutFromNECRewriteAppender(loggerContext, loggerConfig);
    }

    public @NonNull LogEvent rewrite(final @NonNull LogEvent event) {
        final LogEvent rewrite = rewritePolicy.rewrite(event); // only rewrites throwables

        // rewrite logger name/class fq names using mappings from nec
        return Log4jLogEvent.newBuilder()
                .setLoggerName(this.mappings.getOrDefault(rewrite.getLoggerName(), rewrite.getLoggerName()))
                .setMarker(rewrite.getMarker())
                .setLoggerFqcn(this.mappings.getOrDefault(rewrite.getLoggerFqcn(), rewrite.getLoggerFqcn()))
                .setLevel(rewrite.getLevel())
                .setMessage(rewrite.getMessage())
                .setThrown(rewrite.getThrown())
                .setContextMap(rewrite.getContextMap())
                .setContextStack(rewrite.getContextStack())
                .setThreadName(rewrite.getThreadName())
                .setSource(rewrite.getSource())
                .setTimeMillis(rewrite.getTimeMillis())
                .build();
    }

    @SuppressWarnings("unchecked")
    private void loadMappings() {
        try {
            final Field mappingsField = STACKTRACE_DEOBFUSCATOR_CLASS.getDeclaredField("mappings");
            mappingsField.setAccessible(true);
            this.mappings = (Map<String, String>) mappingsField.get(null);
        } catch (final NoSuchFieldException | IllegalAccessException ex) {
            throw new RuntimeException(ex);
        }
    }

    public static @Nullable NotEnoughCrashesRewriter createIfSupported(final @NonNull LoggerContext context, final @NonNull LoggerConfig config) {
        if (NotEnoughCrashesRewriter.supported()) {
            final RewritePolicy policy = NotEnoughCrashesRewriter.findNotEnoughCrashesRewritePolicy(config);
            if (policy != null) {
                return new NotEnoughCrashesRewriter(policy, context, config);
            }
            return null;
        }
        return null;
    }

    private static boolean supported() {
        return STACKTRACE_DEOBFUSCATOR_CLASS != null;
    }

    private static @Nullable Class<?> findClass(final @NonNull String name) {
        try {
            return Class.forName(name);
        } catch (final ClassNotFoundException ex) {
            return null;
        }
    }

    /**
     * Read the RewritePolicy Not Enough Crashes uses to deobfuscate stack traces
     */
    private static @Nullable RewritePolicy findNotEnoughCrashesRewritePolicy(final @NonNull LoggerConfig conf) {
        for (final Appender appender : conf.getAppenders().values()) {
            if (appender.getName().equals("NotEnoughCrashesDeobfuscatingAppender")) {
                try {
                    final Field field = appender.getClass().getDeclaredField("rewritePolicy");
                    field.setAccessible(true);
                    return (RewritePolicy) field.get(appender);
                } catch (final Exception e) {
                    JLineForMcDSrv.LOGGER.error("Couldn't read Not Enough Crashes' rewritePolicy", e);
                }
            }
        }
        return null;
    }

    private void removeSysOutFromNECRewriteAppender(final @NonNull LoggerContext ctx, final @NonNull LoggerConfig conf) {
        /*
         * This method is pretty hacky: we remove, recreate and readd NEC's appender
         * For (MIT-licensed) reference see:
         * https://github.com/natanfudge/Not-Enough-Crashes/blob/147495dd4097017f4d243ead7f7e20d0ccfb7d40/notenoughcrashes/src/main/java/fudge/notenoughcrashes/DeobfuscatingRewritePolicy.java#L17-L41
         */

        conf.removeAppender("NotEnoughCrashesDeobfuscatingAppender");

        // get all AppenderRefs except SysOut
        final List<AppenderRef> appenderRefs = new ArrayList<>(conf.getAppenderRefs());
        appenderRefs.removeIf(ref -> ref.getRef().equals("SysOut"));

        // wrap them in a RewriteAppender
        final RewriteAppender rewriteAppender = RewriteAppender.createAppender(
                "NotEnoughCrashesDeobfuscatingAppender",
                "true",
                appenderRefs.toArray(new AppenderRef[0]),
                ctx.getConfiguration(),
                this.rewritePolicy,
                null
        );
        rewriteAppender.start();

        conf.addAppender(rewriteAppender, null, null);
    }
}
