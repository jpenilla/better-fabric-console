package xyz.jpenilla.endermux.client.runtime;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.IOError;
import java.io.InputStreamReader;
import java.util.concurrent.ExecutorService;
import java.util.function.BooleanSupplier;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.kyori.adventure.text.serializer.ansi.ANSIComponentSerializer;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.jline.reader.EndOfFileException;
import org.jline.reader.Highlighter;
import org.jline.reader.LineReader;
import org.jline.reader.UserInterruptException;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import xyz.jpenilla.endermux.client.transport.ProtocolMismatchException;
import xyz.jpenilla.endermux.client.transport.SocketTransport;
import xyz.jpenilla.endermux.protocol.LayoutConfig;
import xyz.jpenilla.endermux.protocol.Message;
import xyz.jpenilla.endermux.protocol.MessagePayload;
import xyz.jpenilla.endermux.protocol.MessageType;
import xyz.jpenilla.endermux.protocol.Payloads;

import static net.kyori.adventure.text.Component.text;

@NullMarked
final class RemoteConsoleSession {
  private static final String TERMINAL_PROMPT = "> ";
  private static final String DISCONNECTING_MESSAGE = "Disconnecting...";
  private static final String DISCONNECT_HINT_MESSAGE = "Press Ctrl+D to disconnect from console.";
  private static final long SOCKET_POLL_INTERVAL_MS = 500;
  private static final ComponentLogger LOGGER = ComponentLogger.logger(RemoteConsoleSession.class);

  private final String socketPath;
  private final TerminalRuntimeContext terminalContext;
  private final ExecutorService logExecutor;
  private final BooleanSupplier shutdownRequested;
  private final Object interactivityLock = new Object();

  private volatile @Nullable SocketTransport socketClient;
  private volatile boolean interactiveAvailable;
  private @Nullable LineReader lineReader;
  private @Nullable PatternLayout logPatternLayout;

  RemoteConsoleSession(
    final String socketPath,
    final TerminalRuntimeContext terminalContext,
    final ExecutorService logExecutor,
    final BooleanSupplier shutdownRequested
  ) {
    this.socketPath = socketPath;
    this.terminalContext = terminalContext;
    this.logExecutor = logExecutor;
    this.shutdownRequested = shutdownRequested;
  }

  SessionOutcome run() {
    boolean connected = false;
    try {
      this.socketClient = new SocketTransport(this.socketPath);
      this.interactiveAvailable = false;

      final SocketTransport client = this.socketClient;
      client.setDisconnectCallback(this::onDisconnect);
      client.setMessageHandler(this::handleMessage);
      client.connect();
      connected = true;

      LOGGER.info(text()
        .append(text("Connected to Endermux server via socket: ", NamedTextColor.DARK_GREEN, TextDecoration.BOLD))
        .append(text(this.socketPath))
        .build());

      final Highlighter highlighter = new RemoteHighlighter(client);
      this.lineReader = this.terminalContext.createLineReader(client, highlighter);
      TerminalOutput.setLineReader(this.lineReader);

      this.logPatternLayout = this.createLogPattern(client);
      client.sendMessage(Message.unsolicited(MessageType.CLIENT_READY, new Payloads.ClientReady()));

      final boolean userQuit = this.acceptInput();
      return new SessionOutcome(connected, userQuit, userQuit ? DisconnectReason.USER_EOF : null);
    } catch (final ProtocolMismatchException e) {
      LOGGER.error(protocolMismatchMessage(e));
      return new SessionOutcome(connected, true, null);
    } catch (final Exception e) {
      LOGGER.debug("Connection failure", e);
      LOGGER.error("Connection failed: {}", e.getMessage());
      return new SessionOutcome(connected, false, null);
    } finally {
      this.cleanup();
    }
  }

  void disconnect() {
    final SocketTransport client = this.socketClient;
    if (client != null) {
      client.disconnect();
    }
  }

  boolean isConnected() {
    final SocketTransport client = this.socketClient;
    return client != null && client.isConnected();
  }

  private void onDisconnect() {
    this.terminalContext.interruptActiveReader(this.lineReader);
    synchronized (this.interactivityLock) {
      this.interactivityLock.notifyAll();
    }
  }

  private void cleanup() {
    final SocketTransport client = this.socketClient;
    if (client != null) {
      client.disconnect();
    }
    this.socketClient = null;
    this.lineReader = null;
    this.logPatternLayout = null;
    TerminalOutput.setLineReader(null);
    synchronized (this.interactivityLock) {
      this.interactivityLock.notifyAll();
    }
  }

  private boolean acceptInput() {
    if (this.terminalContext.isDumbTerminal()) {
      return this.acceptInputDumb();
    }
    final LineReader sessionReader = this.lineReader;
    if (sessionReader == null) {
      throw new IllegalStateException("LineReader is not initialized");
    }
    return this.acceptInputInteractive(sessionReader);
  }

  private boolean acceptInputInteractive(final LineReader sessionReader) {
    while (true) {
      try {
        final SocketTransport client = this.connectedClient();
        if (client == null) {
          return false;
        }

        if (!this.interactiveAvailable) {
          if (!this.waitForInteractivity()) {
            return false;
          }
          continue;
        }

        final String input = sessionReader.readLine(TERMINAL_PROMPT);
        if (input == null) {
          return this.userRequestedDisconnect();
        }

        final String trimmedInput = input.trim();
        if (trimmedInput.isEmpty()) {
          continue;
        }
        this.sendCommand(client, trimmedInput);
      } catch (final EndOfFileException e) {
        return this.userRequestedDisconnect();
      } catch (final UserInterruptException e) {
        if (this.connectedClient() == null) {
          Thread.interrupted();
          return false;
        }
        if (!this.interactiveAvailable) {
          continue;
        }
        this.printDisconnectHint();
      } catch (final IOError e) {
        Thread.interrupted();
        if (this.connectedClient() == null || this.shutdownRequested.getAsBoolean()) {
          return false;
        }
        LOGGER.debug("Ignoring terminal IO error while reading input", e);
        this.printDisconnectHint();
      }
    }
  }

  private boolean acceptInputDumb() {
    final BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
    while (true) {
      final SocketTransport client = this.connectedClient();
      if (client == null) {
        return false;
      }

      if (!this.interactiveAvailable) {
        if (!this.waitForInteractivity()) {
          return false;
        }
        continue;
      }

      final @Nullable String input;
      try {
        input = this.readInputLine(reader);
      } catch (final IOException e) {
        LOGGER.error("Error reading stdin: {}", e.getMessage());
        LOGGER.debug("Error reading stdin", e);
        return false;
      }

      if (input == null) {
        if (this.connectedClient() == null || this.shutdownRequested.getAsBoolean()) {
          return false;
        }
        if (!this.terminalContext.hasConsoleInput()) {
          this.sleepForInput();
          continue;
        }
        return this.userRequestedDisconnect();
      }

      final String trimmedInput = input.trim();
      if (trimmedInput.isEmpty()) {
        continue;
      }
      this.sendCommand(client, trimmedInput);
    }
  }

  private void sleepForInput() {
    try {
      Thread.sleep(SOCKET_POLL_INTERVAL_MS);
    } catch (final InterruptedException e) {
      if (!this.shutdownRequested.getAsBoolean()) {
        LOGGER.debug("Interrupted while waiting for stdin input", e);
      }
    }
  }

  private @Nullable String readInputLine(final BufferedReader reader) throws IOException {
    while (true) {
      if (this.shutdownRequested.getAsBoolean() || this.connectedClient() == null) {
        return null;
      }
      if (reader.ready()) {
        return reader.readLine();
      }
      this.sleepForInput();
      if (this.shutdownRequested.getAsBoolean()) {
        return null;
      }
    }
  }

  private boolean waitForInteractivity() {
    synchronized (this.interactivityLock) {
      try {
        this.interactivityLock.wait(SOCKET_POLL_INTERVAL_MS);
        return !this.shutdownRequested.getAsBoolean();
      } catch (final InterruptedException e) {
        if (this.shutdownRequested.getAsBoolean()) {
          return false;
        }
        LOGGER.debug("Interrupted while waiting for interactivity update", e);
        return true;
      }
    }
  }

  private void handleMessage(final Message<? extends MessagePayload> message) {
    if (this.connectedClient() == null) {
      LOGGER.warn("Not connected to server");
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

    if (type == MessageType.INTERACTIVITY_STATUS
      && message.payload() instanceof Payloads.InteractivityStatus(boolean available)) {
      this.interactiveAvailable = available;
      if (!available) {
        this.terminalContext.interruptActiveReader(this.lineReader);
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
    TerminalOutput.write(formattedMessage);
  }

  private PatternLayout createLogPattern(final SocketTransport client) {
    final LayoutConfig layoutConfig = client.serverLogLayout();
    if (layoutConfig == null) {
      throw new IllegalStateException("No log layout available from server and no override provided");
    }
    return LayoutConfigLayoutBuilder.toPatternLayout(layoutConfig);
  }

  private void sendCommand(final SocketTransport client, final String input) {
    final Payloads.CommandExecute payload = new Payloads.CommandExecute(input);
    final Message<Payloads.CommandExecute> commandMessage = Message.unsolicited(MessageType.COMMAND_EXECUTE, payload);
    client.sendMessage(commandMessage);
  }

  private @Nullable SocketTransport connectedClient() {
    final SocketTransport client = this.socketClient;
    if (client == null || !client.isConnected()) {
      return null;
    }
    return client;
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

  private boolean userRequestedDisconnect() {
    LOGGER.info(DISCONNECTING_MESSAGE);
    return true;
  }

  private void printDisconnectHint() {
    LOGGER.info(DISCONNECT_HINT_MESSAGE);
  }

  private void printError(final String message, final @Nullable String details) {
    LOGGER.error("Error: {}", message);
    if (details != null) {
      LOGGER.error("Details: {}", details);
    }
  }

  enum DisconnectReason {
    USER_EOF
  }

  record SessionOutcome(boolean connected, boolean stopClient, @Nullable DisconnectReason disconnectReason) {
  }
}
