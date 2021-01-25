package org.chrisoft.jline4mcdsrv;

import net.minecraft.server.dedicated.MinecraftDedicatedServer;
import net.minecraft.util.logging.UncaughtExceptionLogger;
import org.apache.logging.log4j.core.*;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.apache.logging.log4j.LogManager;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;

public class Console
{
    public static void setup(MinecraftDedicatedServer srv)
    {
        Thread conThrd = new Thread("jline4mcdsrv Console Thread")
        {
            public void run()
            {
                Logger logger = (Logger) LogManager.getLogger();

                LineReader lr = LineReaderBuilder.builder()
                    .completer(new MinecraftCommandCompleter(srv.getCommandManager().getDispatcher(), srv.getCommandSource()))
                    .highlighter(new MinecraftCommandHighlighter(srv.getCommandManager().getDispatcher(), srv.getCommandSource()))
                    .option(LineReader.Option.DISABLE_EVENT_EXPANSION, true)
                    .build();

                Appender conAppender = new AbstractAppender("Console", null,
                    PatternLayout.newBuilder().withPattern(JLineForMcDSrvMain.config.logPattern).build(), false)
                {
                    @Override
                    public void append(LogEvent event) {
                        if (lr.isReading())
                            lr.callWidget(lr.CLEAR);

                        lr.getTerminal().writer().print(getLayout().toSerializable(event).toString());

                        if (lr.isReading()) {
                            lr.callWidget(lr.REDRAW_LINE);
                            lr.callWidget(lr.REDISPLAY);
                        }
                        lr.getTerminal().writer().flush();
                    }
                };
                conAppender.start();

                // replace SysOut appender with conAppender
                LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
                LoggerConfig conf = ctx.getConfiguration().getLoggerConfig(logger.getName());
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
        conThrd.setUncaughtExceptionHandler(new UncaughtExceptionLogger(JLineForMcDSrvMain.LOGGER));
        conThrd.start();
    }
}
