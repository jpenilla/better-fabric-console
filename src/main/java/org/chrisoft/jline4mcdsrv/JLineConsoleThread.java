package org.chrisoft.jline4mcdsrv;

import net.minecraft.server.dedicated.DedicatedServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.rewrite.RewriteAppender;
import org.apache.logging.log4j.core.appender.rewrite.RewritePolicy;
import org.apache.logging.log4j.core.config.AppenderRef;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.jetbrains.annotations.Nullable;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.chrisoft.jline4mcdsrv.JLineForMcDSrvMain.LOGGER;

public final class JLineConsoleThread extends Thread {
    private final DedicatedServer server;

    public JLineConsoleThread(final @NonNull DedicatedServer server) {
        super("jline4mcdsrv Console Thread");
        this.server = server;
    }

    @Override
    public void run() {
        LineReader lineReader = LineReaderBuilder.builder()
                .completer(new MinecraftCommandCompleter(this.server.getCommands().getDispatcher(), this.server.createCommandSourceStack()))
                .highlighter(new MinecraftCommandHighlighter(this.server.getCommands().getDispatcher(), this.server.createCommandSourceStack()))
                .option(LineReader.Option.DISABLE_EVENT_EXPANSION, true)
                .build();

        final ConsoleAppender consoleAppender = new ConsoleAppender(lineReader);
        consoleAppender.start();

        final Logger logger = (Logger) LogManager.getLogger();
        final LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        final LoggerConfig conf = ctx.getConfiguration().getLoggerConfig(logger.getName());

        // compatibility hack for Not Enough Crashes
        final RewritePolicy policy = getNECRewritePolicy(conf);
        if (policy != null) {
            consoleAppender.installNotEnoughCrashesRewritePolicy(policy);
            removeSysOutFromNECRewriteAppender(ctx, conf, policy);
        }

        // replace SysOut appender with ConsoleAppender
        conf.removeAppender("SysOut");
        conf.addAppender(consoleAppender, conf.getLevel(), null);
        ctx.updateLoggers();

        while (!this.server.isStopped() && this.server.isRunning()) {
            try {
                final String s = lineReader.readLine("> ");
                if (s.isEmpty()) {
                    continue;
                }
                this.server.handleConsoleInput(s, this.server.createCommandSourceStack());
                if (s.equals("stop")) {
                    break;
                }
            } catch (final EndOfFileException | UserInterruptException ex) {
                this.server.handleConsoleInput("stop", this.server.createCommandSourceStack());
                break;
            }
        }
    }

    /**
     * Read the RewritePolicy Not Enough Crashes uses to deobfuscate stack traces
     */
    @Nullable
    private static RewritePolicy getNECRewritePolicy(final @NonNull LoggerConfig conf) {
        for (final Appender appender : conf.getAppenders().values()) {
            if (appender.getName().equals("NotEnoughCrashesDeobfuscatingAppender")) {
                try {
                    final Field field = appender.getClass().getDeclaredField("rewritePolicy");
                    field.setAccessible(true);
                    return (RewritePolicy) field.get(appender);
                } catch (final Exception e) {
                    LOGGER.error("Couldn't read Not Enough Crashes' rewritePolicy", e);
                }
            }
        }
        return null;
    }

    private static void removeSysOutFromNECRewriteAppender(final @NonNull LoggerContext ctx, final @NonNull LoggerConfig conf, final @NonNull RewritePolicy policy) {
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
                policy,
                null
        );
        rewriteAppender.start();

        conf.addAppender(rewriteAppender, null, null);
    }
}
