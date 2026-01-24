package xyz.jpenilla.endermux.client.runtime;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.serializer.ansi.ANSIComponentSerializer;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.jline.reader.EndOfFileException;
import org.jline.reader.Highlighter;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import xyz.jpenilla.endermux.client.completer.RemoteCommandCompleter;
import xyz.jpenilla.endermux.client.parser.RemoteParser;
import xyz.jpenilla.endermux.client.transport.SocketTransport;
import xyz.jpenilla.endermux.protocol.LayoutConfig;
import xyz.jpenilla.endermux.protocol.Message;
import xyz.jpenilla.endermux.protocol.MessagePayload;
import xyz.jpenilla.endermux.protocol.MessageType;
import xyz.jpenilla.endermux.protocol.Payloads;
import xyz.jpenilla.endermux.jline.MinecraftCompletionMatcher;

@NullMarked
public final class EndermuxClient {

  private static final String TERMINAL_PROMPT = "> ";
  private static final long SOCKET_POLL_INTERVAL_MS = 500;
  private static final Logger LOGGER = LogManager.getLogger();

  private @Nullable LineReader lineReader;
  private @Nullable PatternLayout logPatternLayout;
  private @Nullable SocketTransport socketClient;
  private @Nullable Terminal terminal;

  private final ExecutorService logExecutor = Executors.newSingleThreadExecutor(r -> {
    final Thread thread = new Thread(r, "LogOutput");
    thread.setDaemon(true);
    return thread;
  });

  public void run(final String socketPath) throws Exception {
    this.terminal = this.createTerminal();

    try {
      while (true) {
        final Path path = Paths.get(socketPath);
        this.waitForSocket(path);

        final boolean userQuit = this.runSession(socketPath);
        if (userQuit) {
          break;
        }

        System.out.println("Disconnected from server. Waiting for reconnection...");
      }
    } finally {
      this.shutdown();
      if (this.terminal != null) {
        this.terminal.close();
      }
    }
  }

  private void waitForSocket(final Path socketPath) throws InterruptedException, IOException {
    final Path resolvedSocketPath = socketPath.toAbsolutePath().normalize();
    final String displayPath = socketPath.toString();
    if (Files.exists(resolvedSocketPath)) {
      return;
    }

    final Path parentDir = resolvedSocketPath.getParent();
    if (parentDir == null || !Files.isDirectory(parentDir)) {
      throw new IllegalArgumentException("Parent directory does not exist: " +
        (parentDir != null ? parentDir : resolvedSocketPath));
    }

    System.out.println("Waiting for socket to exist: " + displayPath);

    try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
      parentDir.register(watchService, StandardWatchEventKinds.ENTRY_CREATE);

      if (Files.exists(resolvedSocketPath)) {
        return;
      }

      while (true) {
        final WatchKey key = watchService.take();

        for (final WatchEvent<?> event : key.pollEvents()) {
          if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
            final Path created = parentDir.resolve((Path) event.context());
            if (created.equals(resolvedSocketPath)) {
              return;
            }
          }
        }

        if (!key.reset()) {
          System.err.println("Warning: File watching failed, falling back to polling");
          break;
        }
      }
    } catch (final IOException e) {
      System.err.println("Warning: File watching unavailable (" + e.getMessage() + "), falling back to polling");
    }

    while (!Files.exists(resolvedSocketPath)) {
      Thread.sleep(SOCKET_POLL_INTERVAL_MS);
    }
  }

  private boolean runSession(final String socketPath) {
    try {
      this.socketClient = new SocketTransport(socketPath);

      final Terminal term = this.terminal;
      this.socketClient.setDisconnectCallback(() -> {
        if (term != null) {
          term.raise(Terminal.Signal.INT);
        }
      });

      this.socketClient.connect();
      System.out.println("Connected to Endermux server via socket: " + socketPath);

      this.socketClient.setMessageHandler(this::handleMessage);

      final Highlighter highlighter = new RemoteHighlighter(this.socketClient);

      this.lineReader = this.createLineReader(term, highlighter);

      this.logPatternLayout = this.createLogPattern();
      this.socketClient.sendMessage(Message.unsolicited(MessageType.CLIENT_READY, new Payloads.ClientReady()));

      return this.acceptInput(this.lineReader);
    } catch (final Exception e) {
      LOGGER.debug("Connection failure", e);
      System.err.println("Connection failed: " + e.getMessage());
      return false;
    } finally {
      this.cleanupSession();
    }
  }

  private void cleanupSession() {
    final SocketTransport client = this.socketClient;
    if (client != null) {
      client.disconnect();
      this.socketClient = null;
    }
    this.lineReader = null;
    this.logPatternLayout = null;
  }

  private void shutdown() {
    this.logExecutor.shutdown();
    try {
      if (!this.logExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
        this.logExecutor.shutdownNow();
      }
    } catch (final InterruptedException e) {
      this.logExecutor.shutdownNow();
      Thread.currentThread().interrupt();
    }

    final SocketTransport client = this.socketClient;
    if (client != null) {
      client.disconnect();
    }
  }

  private boolean acceptInput(final LineReader lineReader) {
    while (true) {
      try {
        final SocketTransport client = this.connectedClient();
        if (client == null) {
          return false;
        }

        final String input = lineReader.readLine(TERMINAL_PROMPT);
        if (input == null) {
          return true;
        }

        final String trimmedInput = input.trim();
        if (trimmedInput.isEmpty()) {
          continue;
        }

        this.sendCommand(client, trimmedInput);

      } catch (final EndOfFileException e) {
        return true;
      } catch (final UserInterruptException e) {
        if (this.connectedClient() == null) {
          return false;
        }
        System.out.println("Press Ctrl+D to disconnect from console.");
      }
    }
  }

  private void handleMessage(final Message<? extends MessagePayload> message) {
    if (this.connectedClient() == null) {
      System.err.println("Not connected to server");
      return;
    }

    final MessageType type = message.type();
    if (type == MessageType.LOG_FORWARD && message.payload() instanceof Payloads.LogForward logForward) {
      this.logExecutor.execute(() -> this.processLogMessage(logForward));
      return;
    }

    if (type == MessageType.ERROR && message.payload() instanceof Payloads.Error(String message1, String details)) {
      this.printError(message1, details);
      return;
    }

    if (type == MessageType.CONNECTION_STATUS
      && message.payload() instanceof Payloads.ConnectionStatus(Payloads.ConnectionStatus.Status status)) {
      if (status == Payloads.ConnectionStatus.Status.DISCONNECTED) {
        System.out.println("Disconnected from server.");
      }
    }
  }

  private void processLogMessage(final Payloads.LogForward logForward) {
    final String logger = logForward.logger();
    final String level = logForward.level();
    String logMessage = logForward.message();
    if (logForward.componentMessageJson() != null) {
      logMessage = ANSIComponentSerializer.ansi().serialize(
        GsonComponentSerializer.gson().deserialize(logForward.componentMessageJson())
      );
    }
    final long timestamp = logForward.timestamp();
    final String threadName = logForward.thread();

    this.printLogMessage(this.formatLogMessage(logger, level, logMessage, timestamp, threadName));
  }

  private String formatLogMessage(
    final String logger,
    final String level,
    final String logMessage,
    final long timestamp,
    final String threadName
  ) {
    final PatternLayout layout = this.logPatternLayout;
    if (layout == null) {
      throw new IllegalStateException("Log pattern layout not initialized");
    }
    final LogEvent logEvent = new SimpleLogEvent(
      logger,
      Level.valueOf(level),
      logMessage,
      timestamp,
      threadName
    );
    return layout.toSerializable(logEvent);
  }

  private void printLogMessage(final String formattedMessage) {
    final LineReader reader = this.lineReader;

    if (reader != null && reader.isReading()) {
      reader.callWidget(LineReader.CLEAR);
    }

    if (reader != null) {
      reader.getTerminal().writer().print(formattedMessage);
    } else {
      System.out.print(formattedMessage);
    }

    if (reader != null && reader.isReading()) {
      reader.callWidget(LineReader.REDRAW_LINE);
      reader.callWidget(LineReader.REDISPLAY);
    }

    if (reader != null) {
      reader.getTerminal().writer().flush();
    }
  }

  private Terminal createTerminal() throws IOException {
    return TerminalBuilder.builder()
      .system(true)
      .build();
  }

  private LineReader createLineReader(final @Nullable Terminal term, final Highlighter highlighter) {
    return LineReaderBuilder.builder()
      .appName("Endermux Client")
      .terminal(term)
      .completer(new RemoteCommandCompleter(this.socketClient))
      .highlighter(highlighter)
      .parser(new RemoteParser(this.socketClient))
      .completionMatcher(new MinecraftCompletionMatcher())
      .option(LineReader.Option.INSERT_TAB, false)
      .option(LineReader.Option.DISABLE_EVENT_EXPANSION, true)
      .option(LineReader.Option.COMPLETE_IN_WORD, true)
      .build();
  }

  private PatternLayout createLogPattern() {
    final LayoutConfig layoutConfig = this.socketClient != null ? this.socketClient.serverLogLayout() : null;
    if (layoutConfig == null) {
      throw new IllegalStateException("No log layout available from server and no override provided");
    }
    return LayoutConfigLayoutBuilder.toPatternLayout(layoutConfig);
  }

  private void sendCommand(final SocketTransport client, final String input) {
    final Payloads.CommandExecute payload = new Payloads.CommandExecute(input);
    final Message<Payloads.CommandExecute> commandMessage = client.createRequest(MessageType.COMMAND_EXECUTE, payload);
    client.sendMessage(commandMessage);
  }

  private @Nullable SocketTransport connectedClient() {
    final SocketTransport client = this.socketClient;
    if (client == null || !client.isConnected()) {
      return null;
    }
    return client;
  }

  private void printError(final String message, final @Nullable String details) {
    System.err.println("Error: " + message);
    if (details != null) {
      System.err.println("Details: " + details);
    }
  }
}
