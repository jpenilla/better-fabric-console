package org.chrisoft.jline4mcdsrv;

import java.nio.file.Paths;
import net.minecraft.server.dedicated.DedicatedServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;

public final class ConsoleThread extends Thread {
    private static final String TERMINAL_PROMPT = "> ";
    private static final String STOP_COMMAND = "stop";

    private final DedicatedServer server;
    private final LineReader lineReader;

    public ConsoleThread(final @NonNull DedicatedServer server) {
        super("Console thread");
        this.server = server;
        this.lineReader = this.buildLineReader();
    }

    private @NonNull LineReader buildLineReader() {
        return LineReaderBuilder.builder()
                .appName("Fabric Dedicated Server")
                .variable(LineReader.HISTORY_FILE, Paths.get(".console_history"))
                .completer(new MinecraftCommandCompleter(this.server.getCommands().getDispatcher(), this.server.createCommandSourceStack()))
                .highlighter(new MinecraftCommandHighlighter(this.server.getCommands().getDispatcher(), this.server.createCommandSourceStack()))
                .option(LineReader.Option.INSERT_TAB, false)
                .option(LineReader.Option.DISABLE_EVENT_EXPANSION, true)
                .option(LineReader.Option.COMPLETE_IN_WORD, true)
                .build();
    }

    public void init() {
        final ConsoleAppender consoleAppender = new ConsoleAppender(this.lineReader);
        consoleAppender.start();

        final Logger logger = (Logger) LogManager.getRootLogger();
        final LoggerContext loggerContext = (LoggerContext) LogManager.getContext(false);
        final LoggerConfig loggerConfig = loggerContext.getConfiguration().getLoggerConfig(logger.getName());

        final NotEnoughCrashesRewriter rewriter = NotEnoughCrashesRewriter.createIfSupported(loggerContext, loggerConfig);
        consoleAppender.setRewriter(rewriter);

        // replace SysOut appender with ConsoleAppender
        loggerConfig.removeAppender("SysOut");
        loggerConfig.addAppender(consoleAppender, loggerConfig.getLevel(), null);
        loggerContext.updateLoggers();
    }

    @Override
    public void run() {
        JLineForMcDSrv.LOGGER.info("Done initializing jline4mcdsrv console thread.");
        this.acceptInput();
    }

    private boolean isRunning() {
        return !this.server.isStopped() && this.server.isRunning();
    }

    private void acceptInput() {
        while (this.isRunning()) {
            try {
                final String input = this.lineReader.readLine(TERMINAL_PROMPT).trim();
                if (input.isEmpty()) {
                    continue;
                }
                this.server.handleConsoleInput(input, this.server.createCommandSourceStack());
                if (input.equals(STOP_COMMAND)) {
                    break;
                }
            } catch (final EndOfFileException | UserInterruptException ex) {
                this.server.handleConsoleInput(STOP_COMMAND, this.server.createCommandSourceStack());
                break;
            }
        }
    }
}
