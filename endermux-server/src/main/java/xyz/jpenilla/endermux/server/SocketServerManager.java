package xyz.jpenilla.endermux.server;

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.jpenilla.endermux.protocol.Message;
import xyz.jpenilla.endermux.protocol.MessagePayload;
import xyz.jpenilla.endermux.protocol.MessageSerializer;
import xyz.jpenilla.endermux.protocol.MessageType;
import xyz.jpenilla.endermux.protocol.Payloads;
import xyz.jpenilla.endermux.protocol.SocketProtocolConstants;
import xyz.jpenilla.endermux.server.api.ServerHooks;
import xyz.jpenilla.endermux.server.handlers.CommandHandler;
import xyz.jpenilla.endermux.server.handlers.CompletionHandler;
import xyz.jpenilla.endermux.server.handlers.HandlerRegistry;
import xyz.jpenilla.endermux.server.handlers.ParseHandler;
import xyz.jpenilla.endermux.server.handlers.SyntaxHighlightHandler;

@NullMarked
public final class SocketServerManager {
  private static final Logger LOGGER = LoggerFactory.getLogger(SocketServerManager.class);

  private final Set<ClientEndpoint> connections = ConcurrentHashMap.newKeySet();
  private final ConcurrentHashMap<ClientEndpoint, ClientSession> sessions = new ConcurrentHashMap<>();
  private final ExecutorService executor;
  private final ServerHooks hooks;
  private final HandlerRegistry handlerRegistry;
  private final MessageSerializer serializer;
  private final Path socketPath;
  private final int maxConnections;
  private final ServerHooks.ServerMetadata metadata;

  private final AtomicBoolean running = new AtomicBoolean(false);
  private ServerSocketChannel serverChannel;
  private Thread acceptorThread;

  public SocketServerManager(
    final ServerHooks hooks,
    final Path socketPath,
    final int maxConnections
  ) {
    this.hooks = hooks;
    this.socketPath = socketPath;
    this.maxConnections = maxConnections;
    this.metadata = hooks.metadata();
    this.executor = Executors.newThreadPerTaskExecutor(
      Thread.ofVirtual()
        .name("SocketWorker-", 0)
        .factory()
    );
    this.handlerRegistry = new HandlerRegistry();
    this.serializer = MessageSerializer.createStandard();

    this.registerHandlers();
  }

  private void registerHandlers() {
    this.handlerRegistry.register(new CompletionHandler(this.hooks));
    this.handlerRegistry.register(new SyntaxHighlightHandler(this.hooks));
    this.handlerRegistry.register(new ParseHandler(this.hooks));
    this.handlerRegistry.register(new CommandHandler(this.hooks));
  }

  public void start() {
    if (this.running.compareAndSet(false, true)) {
      try {
        java.nio.file.Files.deleteIfExists(this.socketPath);

        final UnixDomainSocketAddress address = UnixDomainSocketAddress.of(this.socketPath);

        this.serverChannel = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
        this.serverChannel.bind(address);

        this.acceptorThread = Thread.ofVirtual().name("SocketAcceptor").unstarted(this::acceptConnections);
        this.acceptorThread.start();

        LOGGER.info("Listening for console socket connections at: {}", this.socketPath);
      } catch (final IOException e) {
        LOGGER.error("Failed to start console socket server", e);
        this.running.set(false);
        this.stop();
      }
    }
  }

  public void stop() {
    if (this.running.compareAndSet(true, false)) {
      LOGGER.info("Stopping console socket server...");
      this.closeServerChannel();
      this.closeConnections();
      this.shutdownExecutor();
      this.joinAcceptor();
      this.deleteSocketFile();
      LOGGER.info("Console socket server stopped");
    }
  }

  private void acceptConnections() {
    while (this.running.get()) {
      try {
        final SocketChannel clientChannel = this.serverChannel.accept();
        if (clientChannel != null) {
          if (this.connections.size() >= this.maxConnections) {
            LOGGER.warn("Maximum socket connections ({}) reached, rejecting new connection", this.maxConnections);
            clientChannel.close();
            continue;
          }

          this.handleNewConnection(clientChannel);
        }
      } catch (final IOException e) {
        if (this.running.get()) {
          LOGGER.error("Error accepting socket connection", e);
        }
      }
    }
  }

  private void handleNewConnection(final SocketChannel clientChannel) {
    try {
      final ClientEndpoint connection = new ClientEndpoint(clientChannel, this.serializer);
      final ClientSession session = new ClientSession(connection, this.handlerRegistry);
      this.sessions.put(connection, session);
      connection.start(session, () -> this.removeConnection(connection));
      this.executor.submit(() -> this.runConnection(connection, session));

    } catch (final IOException e) {
      LOGGER.error("Failed to setup client connection", e);
      try {
        clientChannel.close();
      } catch (final IOException ignored) {
      }
    }
  }

  private void runConnection(final ClientEndpoint connection, final ClientSession session) {
    try {
      if (!this.performHandshake(connection)) {
        connection.close();
        return;
      }

      this.connections.add(connection);
      LOGGER.info("New console socket connection established ({} total connections)", this.connections.size());

      session.initialize();

      connection.run();
    } catch (final IOException e) {
      LOGGER.debug("Connection setup failed", e);
      connection.close();
    }
  }

  private boolean performHandshake(final ClientEndpoint connection) throws IOException {
    final Message<?> message = connection.readInitialMessage(SocketProtocolConstants.HANDSHAKE_TIMEOUT_MS);
    if (message == null) {
      this.sendHandshakeResponse(connection, null, new Payloads.Reject("Handshake timeout", SocketProtocolConstants.PROTOCOL_VERSION));
      return false;
    }
    final @Nullable String requestId = message.requestId();
    if (requestId == null) {
      this.sendHandshakeResponse(connection, null, new Payloads.Reject("Missing requestId", SocketProtocolConstants.PROTOCOL_VERSION));
      return false;
    }
    if (message.type() != MessageType.HELLO || !(message.payload() instanceof Payloads.Hello hello)) {
      this.sendHandshakeResponse(connection, requestId, new Payloads.Reject("Expected HELLO", SocketProtocolConstants.PROTOCOL_VERSION));
      return false;
    }

    if (hello.protocolVersion() != SocketProtocolConstants.PROTOCOL_VERSION) {
      this.sendHandshakeResponse(connection, requestId, new Payloads.Reject("Unsupported protocol version", SocketProtocolConstants.PROTOCOL_VERSION));
      return false;
    }

    this.sendHandshakeResponse(connection, requestId, this.welcomePayload());
    return true;
  }

  private void sendHandshakeResponse(
    final ClientEndpoint connection,
    final @Nullable String requestId,
    final MessagePayload payload
  ) {
    final MessageType type = MessageType.responseTypeForPayload(payload);
    final Message<?> message = requestId == null
      ? Message.unsolicited(type, payload)
      : Message.response(requestId, type, payload);
    connection.send(message);
  }

  private Payloads.Welcome welcomePayload() {
    return new Payloads.Welcome(
      SocketProtocolConstants.PROTOCOL_VERSION,
      this.metadata.logLayout()
    );
  }

  private void closeServerChannel() {
    if (this.serverChannel != null) {
      try {
        this.serverChannel.close();
      } catch (final IOException e) {
        LOGGER.error("Error closing server channel", e);
      }
    }
  }

  private void closeConnections() {
    this.connections.forEach(ClientEndpoint::close);
    this.connections.clear();
    this.sessions.clear();
  }

  private void shutdownExecutor() {
    this.executor.shutdown();
    try {
      if (!this.executor.awaitTermination(5, TimeUnit.SECONDS)) {
        this.executor.shutdownNow();
      }
    } catch (final InterruptedException e) {
      this.executor.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }

  private void joinAcceptor() {
    if (this.acceptorThread != null) {
      try {
        this.acceptorThread.join();
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }

  private void deleteSocketFile() {
    try {
      java.nio.file.Files.deleteIfExists(this.socketPath);
    } catch (final IOException e) {
      LOGGER.error("Failed to delete socket file: {}", this.socketPath, e);
    }
  }

  void removeConnection(final ClientEndpoint connection) {
    this.sessions.remove(connection);
    if (this.connections.remove(connection)) {
      LOGGER.info("Console socket connection closed ({} total connections)", this.connections.size());
    }
  }

  public void broadcastLog(final Payloads.LogForward payload) {
    final Message<?> message = Message.unsolicited(MessageType.LOG_FORWARD, payload);
    for (final ClientEndpoint connection : this.connections) {
      final ClientSession session = this.sessions.get(connection);
      if (session != null && session.isLogReady()) {
        connection.send(message);
      }
    }
  }

  public boolean isRunning() {
    return this.running.get();
  }
}
