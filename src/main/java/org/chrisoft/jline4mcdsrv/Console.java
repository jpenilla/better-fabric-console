package org.chrisoft.jline4mcdsrv;

import net.minecraft.server.dedicated.MinecraftDedicatedServer;
import net.minecraft.util.logging.UncaughtExceptionLogger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.rewrite.RewriteAppender;
import org.apache.logging.log4j.core.appender.rewrite.RewritePolicy;
import org.apache.logging.log4j.core.config.AppenderRef;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.jetbrains.annotations.Nullable;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.chrisoft.jline4mcdsrv.JLineForMcDSrvMain.LOGGER;

public class Console
{
    public static void setup(MinecraftDedicatedServer srv)
    {
        Thread conThrd = new Thread("jline4mcdsrv Console Thread")
        {
            public void run()
            {
                LineReader lr = LineReaderBuilder.builder()
                    .completer(new MinecraftCommandCompleter(srv.getCommandManager().getDispatcher(), srv.getCommandSource()))
                    .highlighter(new MinecraftCommandHighlighter(srv.getCommandManager().getDispatcher(), srv.getCommandSource()))
                    .option(LineReader.Option.DISABLE_EVENT_EXPANSION, true)
                    .build();

                ConsoleAppender conAppender = new ConsoleAppender(lr);
                conAppender.start();

                Logger logger = (Logger) LogManager.getLogger();
                LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
                LoggerConfig conf = ctx.getConfiguration().getLoggerConfig(logger.getName());

                // compatibility hack for Not Enough Crashes
                RewritePolicy policy = getNECRewritePolicy(conf);
                if (policy != null) {
                    conAppender.setRewritePolicy(policy);
                    removeSysOutFromNECRewriteAppender(ctx, conf, policy);
                }

                // replace SysOut appender with Console appender
                conf.removeAppender("SysOut");
                conf.addAppender(conAppender, conf.getLevel(), null);
                ctx.updateLoggers();

                while (!srv.isStopped() && srv.isRunning()) {
                    try {
                        String s = lr.readLine("/");
                        srv.enqueueCommand(s, srv.getCommandSource());
                        if (s.equals("stop"))
                            break;
                    } catch (EndOfFileException|UserInterruptException e) {
                        srv.enqueueCommand("stop", srv.getCommandSource());
                        break;
                    }
                }
            }
        };
        conThrd.setDaemon(true);
        conThrd.setUncaughtExceptionHandler(new UncaughtExceptionLogger(LOGGER));
        conThrd.start();
    }

    /** Read the RewritePolicy Not Enough Crashes uses to deobfuscate stack traces */
    @Nullable
    private static RewritePolicy getNECRewritePolicy(LoggerConfig conf) {
        for (Appender appender : conf.getAppenders().values()) {
            if (appender.getName().equals("NotEnoughCrashesDeobfuscatingAppender")) {
                try {
                    Field field = appender.getClass().getDeclaredField("rewritePolicy");
                    field.setAccessible(true);
                    return (RewritePolicy) field.get(appender);
                } catch (Exception e) {
                    LOGGER.error("Couldn't read Not Enough Crashes' rewritePolicy", e);
                }
            }
        }

        return null;
    }

    private static void removeSysOutFromNECRewriteAppender(LoggerContext ctx, LoggerConfig conf, RewritePolicy policy) {
        /*
         * This method is pretty hacky: we remove, recreate and readd NEC's appender
         * For (MIT-licensed) reference see:
         * https://github.com/natanfudge/Not-Enough-Crashes/blob/147495dd4097017f4d243ead7f7e20d0ccfb7d40/notenoughcrashes/src/main/java/fudge/notenoughcrashes/DeobfuscatingRewritePolicy.java#L17-L41
         */

        conf.removeAppender("NotEnoughCrashesDeobfuscatingAppender");

        // get all AppenderRefs except SysOut
        List<AppenderRef> appenderRefs = new ArrayList<>(conf.getAppenderRefs());
        appenderRefs.removeIf((ref) -> ref.getRef().equals("SysOut"));

        // wrap them in a RewriteAppender
        RewriteAppender rewriteAppender = RewriteAppender.createAppender(
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
