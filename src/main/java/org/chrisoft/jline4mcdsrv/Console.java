package org.chrisoft.jline4mcdsrv;

import net.minecraft.server.dedicated.MinecraftDedicatedServer;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.LogEvent;
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
    private LineReader lr;
    private MinecraftDedicatedServer srv;
    private MinecraftCommandCompleter mcc;
    private MinecraftCommandHighlighter mch;
    public Console(MinecraftDedicatedServer _srv)
    {
        srv=_srv;
    }
    public void setup()
    {
        Thread conthrd = new Thread("Server Console Thread") {
            public void run() {
                mcc = new MinecraftCommandCompleter(srv.getCommandManager().getDispatcher(), srv.getCommandSource());
                mch = new MinecraftCommandHighlighter(srv.getCommandManager().getDispatcher(), srv.getCommandSource());
                lr = LineReaderBuilder.builder()
                        .completer(mcc)
                        .highlighter(mch)
                        .option(LineReader.Option.DISABLE_EVENT_EXPANSION, true)
                        .build();
                org.apache.logging.log4j.core.Appender conappender = new AbstractAppender("Console", null, PatternLayout.newBuilder().withPattern("%style{[%d{HH:mm:ss}]}{blue} %highlight{[%t/%level]}{FATAL=red, ERROR=red, WARN=yellow, INFO=green, DEBUG=green, TRACE=blue} %style{(%logger{1})}{cyan} %highlight{%msg%n}{FATAL=red, ERROR=red, WARN=normal, INFO=normal, DEBUG=normal, TRACE=normal}").build(), false) {
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
                conappender.start();

                org.apache.logging.log4j.core.Logger l = (org.apache.logging.log4j.core.Logger) LogManager.getLogger();
                l.removeAppender(l.getAppenders().get("SysOut"));
                LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
                LoggerConfig conf = ctx.getConfiguration().getLoggerConfig(LogManager.getLogger().getName());
                Level level = conf.getLevel();
                conf.addAppender(conappender, level, null);
                ctx.updateLoggers();

                String s;
                while (true) {
                    try {
                        s = lr.readLine("/");
                        srv.enqueueCommand(s, srv.getCommandSource());
                        if (s.equals("stop"))
                            break;
                    }
                    catch (UserInterruptException e) {}
                    catch (EndOfFileException e) {
                        srv.enqueueCommand("stop", srv.getCommandSource());
                        return;
                    }
                }
            }
        };
        conthrd.setDaemon(true);
        conthrd.start();
    }
}
