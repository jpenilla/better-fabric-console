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
import java.util.Locale;
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
import xyz.jpenilla.endermux.client.transport.ProtocolMismatchException;
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
  private boolean lastSessionConnected;
  private volatile boolean interactiveAvailable;
  private @Nullable ExitReason exitReason;
  private volatile boolean shutdownRequested;
  private final Object interactivityLock = new Object();

  private final ExecutorService logExecutor = Executors.newSingleThreadExecutor(r -> {
    final Thread thread = new Thread(r, "LogOutput");
    thread.setDaemon(true);
    return thread;
  });

  public void run(final String socketPath) throws Exception {
    this.terminal = this.createTerminal();

    try {
      this.registerSignalHandlers();
      int retryCount = 0;
      while (true) {
        if (this.shutdownRequested) {
          break;
        }
        final Path path = Paths.get(socketPath);
        this.waitForSocket(path);

        if (this.shutdownRequested) {
          break;
        }

        final boolean userQuit = this.runSession(socketPath);
        if (userQuit) {
          break;
        }

        if (this.lastSessionConnected) {
          retryCount = 0;
        }

        retryCount++;
        final long backoffMs = this.retryBackoffMs(retryCount);

        System.out.println("Disconnected from server. Waiting for reconnection...");
        if (backoffMs > 0) {
          System.out.println("Reconnecting in " + formatBackoff(backoffMs) + "...");
          Thread.sleep(backoffMs);
        }
      }
    } finally {
      this.shutdown();
      this.printFarewellIfNeeded();
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
        if (this.shutdownRequested) {
          return;
        }
        final WatchKey key = watchService.poll(SOCKET_POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);
        if (key == null) {
          continue;
        }

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
      if (this.shutdownRequested) {
        return;
      }
      Thread.sleep(SOCKET_POLL_INTERVAL_MS);
    }
  }

  private boolean runSession(final String socketPath) {
    this.lastSessionConnected = false;
    try {
      this.socketClient = new SocketTransport(socketPath);
      this.interactiveAvailable = false;

      final Terminal term = this.terminal;
      this.socketClient.setDisconnectCallback(() -> {
        final LineReader reader = this.lineReader;
        if (term != null && reader != null && reader.isReading()) {
          term.raise(Terminal.Signal.INT);
        }
      });
      this.socketClient.setMessageHandler(this::handleMessage);

      this.socketClient.connect();
      this.lastSessionConnected = true;
      System.out.println("Connected to Endermux server via socket: " + socketPath);

      final Highlighter highlighter = new RemoteHighlighter(this.socketClient);

      this.lineReader = this.createLineReader(term, highlighter);

      this.logPatternLayout = this.createLogPattern();
      this.socketClient.sendMessage(Message.unsolicited(MessageType.CLIENT_READY, new Payloads.ClientReady()));

      return this.acceptInput(this.lineReader);
    } catch (final ProtocolMismatchException e) {
      System.err.println(protocolMismatchMessage(e));
      return true;
    } catch (final Exception e) {
      LOGGER.debug("Connection failure", e);
      System.err.println("Connection failed: " + e.getMessage());
      return false;
    } finally {
      this.cleanupSession();
    }
  }

  private void registerSignalHandlers() {
    final Terminal term = this.terminal;
    if (term == null) {
      return;
    }
    term.handle(Terminal.Signal.INT, signal -> {
      if (this.connectedClient() == null) {
        this.exitReason = ExitReason.USER_INTERRUPT_WHILE_WAITING;
        this.shutdownRequested = true;
      }
    });
  }

  private static String protocolMismatchMessage(final ProtocolMismatchException e) {
    final StringBuilder message = new StringBuilder();
    message.append("Protocol mismatch: server expects v");
    message.append(e.expectedVersion());
    message.append(", client is v");
    message.append(e.actualVersion());
    final String reason = e.reason();
    if (reason != null && !reason.isBlank()) {
      message.append(". Reason: ");
      message.append(reason);
    }
    message.append(". Please update client/server to matching versions.");
    return message.toString();
  }

  private long retryBackoffMs(final int attempt) {
    return switch (attempt) {
      case 1 -> 0L;
      case 2 -> 500L;
      case 3 -> 1000L;
      case 4 -> 2000L;
      case 5 -> 3000L;
      case 6 -> 4000L;
      default -> 5000L;
    };
  }

  private static String formatBackoff(final long backoffMs) {
    if (backoffMs % 1000L == 0) {
      return Long.toString(backoffMs / 1000L) + "s";
    }
    final double seconds = backoffMs / 1000.0;
    return String.format(Locale.ROOT, "%.1fs", seconds);
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

  private void printFarewellIfNeeded() {
    if (this.exitReason == null) {
      return;
    }
    System.out.println("Goodbye!");
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

        if (!this.interactiveAvailable) {
          this.waitForInteractivity();
          continue;
        }

        final String input = lineReader.readLine(TERMINAL_PROMPT);
        if (input == null) {
          this.exitReason = ExitReason.USER_EOF;
          System.out.println("Disconnecting...");
          return true;
        }

        final String trimmedInput = input.trim();
        if (trimmedInput.isEmpty()) {
          continue;
        }

        this.sendCommand(client, trimmedInput);

      } catch (final EndOfFileException e) {
        this.exitReason = ExitReason.USER_EOF;
        System.out.println("Disconnecting...");
        return true;
      } catch (final UserInterruptException e) {
        if (this.connectedClient() == null) {
          return false;
        }
        if (!this.interactiveAvailable) {
          continue;
        }
        System.out.println("Press Ctrl+D to disconnect from console.");
      }
    }
  }

  private void waitForInteractivity() {
    synchronized (this.interactivityLock) {
      try {
        this.interactivityLock.wait(SOCKET_POLL_INTERVAL_MS);
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }

  private enum ExitReason {
    USER_EOF,
    USER_INTERRUPT_WHILE_WAITING
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
      return;
    }

    if (type == MessageType.INTERACTIVITY_STATUS
      && message.payload() instanceof Payloads.InteractivityStatus(boolean available)) {
      this.interactiveAvailable = available;

      final LineReader reader = this.lineReader;
      final Terminal term = this.terminal;
      if (!available && reader != null && reader.isReading() && term != null) {
        term.raise(Terminal.Signal.INT);
      }

      synchronized (this.interactivityLock) {
        this.interactivityLock.notifyAll();
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

    this.printLogMessage(this.formatLogMessage(
      logger,
      level,
      logMessage,
      timestamp,
      threadName,
      logForward.throwable()
    ));
  }

  private String formatLogMessage(
    final String logger,
    final String level,
    final String logMessage,
    final long timestamp,
    final String threadName,
    final Payloads.@Nullable ThrowableInfo throwable
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
      threadName,
      ThrowableInfoUtil.toThrowable(throwable)
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
