package xyz.jpenilla.endermux.client.transport;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.jpenilla.endermux.protocol.ConnectionState;
import xyz.jpenilla.endermux.protocol.FrameCodec;
import xyz.jpenilla.endermux.protocol.LayoutConfig;
import xyz.jpenilla.endermux.protocol.Message;
import xyz.jpenilla.endermux.protocol.MessagePayload;
import xyz.jpenilla.endermux.protocol.MessageSerializer;
import xyz.jpenilla.endermux.protocol.MessageType;
import xyz.jpenilla.endermux.protocol.Payloads;
import xyz.jpenilla.endermux.protocol.SocketProtocolConstants;
import xyz.jpenilla.endermux.protocol.TimedRead;

@NullMarked
public final class SocketTransport {

  private static final Logger LOGGER = LoggerFactory.getLogger(SocketTransport.class);

  private final String socketPath;
  private final MessageSerializer serializer;
  private final ConcurrentHashMap<String, CompletableFuture<Message<?>>> pendingRequests = new ConcurrentHashMap<>();
  private final AtomicReference<ConnectionState> state =
    new AtomicReference<>(ConnectionState.DISCONNECTED);
  private final Object writeLock = new Object();

  private @Nullable SocketChannel socketChannel;
  private @Nullable DataInputStream reader;
  private @Nullable DataOutputStream writer;
  private volatile @Nullable TransportMessageHandler messageHandler;
  private volatile @Nullable Runnable disconnectCallback;
  private volatile @Nullable LayoutConfig serverLogLayout;
  private volatile boolean interactivityAvailable;

  public SocketTransport(final String socketPath) {
    this.socketPath = socketPath;
    this.serializer = MessageSerializer.createStandard();
  }

  public void connect() throws IOException, ProtocolMismatchException {
    if (!this.state.compareAndSet(ConnectionState.DISCONNECTED, ConnectionState.CONNECTING)) {
      throw new IllegalStateException("Cannot connect: current state is " + this.state.get());
    }

    try {
      final UnixDomainSocketAddress address = UnixDomainSocketAddress.of(this.socketPath);

      this.socketChannel = SocketChannel.open(StandardProtocolFamily.UNIX);
      this.socketChannel.connect(address);

      this.reader = new DataInputStream(new BufferedInputStream(Channels.newInputStream(this.socketChannel)));
      this.writer = new DataOutputStream(new BufferedOutputStream(Channels.newOutputStream(this.socketChannel)));

      this.performHandshake();

      this.state.set(ConnectionState.CONNECTED);

      final Thread receiverThread = new Thread(this::receiveMessages, "SocketTransport-Receiver");
      receiverThread.setDaemon(true);
      receiverThread.start();
    } catch (final IOException | ProtocolMismatchException e) {
      this.closeResources();
      this.state.set(ConnectionState.DISCONNECTED);
      throw e;
    }
  }

  public void disconnect() {
    if (this.state.compareAndSet(ConnectionState.CONNECTED, ConnectionState.DISCONNECTING)
      || this.state.compareAndSet(ConnectionState.CONNECTING, ConnectionState.DISCONNECTING)) {
      this.failPendingRequests(new IOException("Connection closed"));
      this.closeResources();
      this.state.set(ConnectionState.DISCONNECTED);
    }
  }

  public ConnectionState getState() {
    return this.state.get();
  }

  public boolean isConnected() {
    return this.state.get() == ConnectionState.CONNECTED;
  }

  public boolean sendMessage(final Message<?> message) {
    if (this.writer != null && this.isConnected()) {
      try {
        this.writeMessage(message);
        return true;
      } catch (final IOException e) {
        this.disconnect();
      }
    }
    return false;
  }

  public Message<?> sendMessageAndWaitForResponse(
    final Message<?> message,
    final MessageType expectedResponseType,
    final long timeoutMs
  ) throws IOException, InterruptedException {
    if (requiresInteractivity(message.type()) && !this.interactivityAvailable) {
      throw new IOException("Interactivity is currently unavailable");
    }

    if (message.requestId() == null) {
      throw new IOException("Request message is missing requestId");
    }
    final String requestId = message.requestId();

    final CompletableFuture<Message<?>> future = new CompletableFuture<>();
    this.pendingRequests.put(requestId, future);

    try {
      if (!this.sendMessage(message)) {
        throw new IOException("Not connected");
      }
      return this.awaitResponse(future, expectedResponseType, timeoutMs);
    } catch (final ExecutionException | TimeoutException e) {
      throw this.wrapResponseException(expectedResponseType, timeoutMs, e);
    } finally {
      this.pendingRequests.remove(requestId);
    }
  }

  public String getSyntaxHighlight(final String command) throws IOException, InterruptedException {
    final Payloads.SyntaxHighlightRequest payload = new Payloads.SyntaxHighlightRequest(command);
    final Message<Payloads.SyntaxHighlightRequest> request = this.createRequest(MessageType.SYNTAX_HIGHLIGHT_REQUEST, payload);

    final Message<?> response = this.sendMessageAndWaitForResponse(
      request,
      MessageType.SYNTAX_HIGHLIGHT_RESPONSE,
      SocketProtocolConstants.SYNTAX_HIGHLIGHT_TIMEOUT_MS
    );

    if (response.payload() instanceof Payloads.SyntaxHighlightResponse highlightResponse) {
      return highlightResponse.highlighted();
    }
    return command;
  }

  private void receiveMessages() {
    try {
      while (this.isConnected() && this.socketChannel != null && this.socketChannel.isOpen()) {
        final Message<?> message = this.readMessage();
        if (message == null) {
          break;
        }

        this.handleResponse(message);
      }
    } catch (final IOException e) {
      LOGGER.debug("Socket client read error", e);
    } finally {
      this.disconnect();

      final Runnable callback = this.disconnectCallback;
      if (callback != null) {
        callback.run();
      }
    }
  }

  public void handleResponse(final Message<?> message) {
    if (message.type() == MessageType.INTERACTIVITY_STATUS
      && message.payload() instanceof Payloads.InteractivityStatus(boolean available)) {
      this.interactivityAvailable = available;
    }

    if (message.requestId() != null) {
      final CompletableFuture<Message<?>> future = this.pendingRequests.remove(message.requestId());
      if (future != null) {
        future.complete(message);
      }
    }

    final TransportMessageHandler handler = this.messageHandler;
    if (handler != null) {
      handler.handleMessage(message);
    }
  }

  public void setMessageHandler(final TransportMessageHandler handler) {
    this.messageHandler = handler;
  }

  public void setDisconnectCallback(final Runnable callback) {
    this.disconnectCallback = callback;
  }

  public @Nullable LayoutConfig serverLogLayout() {
    return this.serverLogLayout;
  }

  public boolean isInteractivityAvailable() {
    return this.interactivityAvailable;
  }

  private void performHandshake() throws IOException, ProtocolMismatchException {
    final Payloads.Hello hello = new Payloads.Hello(SocketProtocolConstants.PROTOCOL_VERSION);
    final Message<Payloads.Hello> helloMessage = Message.<Payloads.Hello>builder(MessageType.HELLO)
      .requestId(UUID.randomUUID())
      .payload(hello)
      .build();
    this.writeMessage(helloMessage);

    final Message<?> response = this.readMessageWithTimeout(SocketProtocolConstants.HANDSHAKE_TIMEOUT_MS);
    if (response == null) {
      throw new IOException("Handshake timeout");
    }

    if (response.type() == MessageType.REJECT && response.payload() instanceof Payloads.Reject(String reason, int expectedVersion)) {
      if (expectedVersion != SocketProtocolConstants.PROTOCOL_VERSION) {
        throw new ProtocolMismatchException(
          "Handshake rejected: " + reason,
          expectedVersion,
          SocketProtocolConstants.PROTOCOL_VERSION
        );
      }
      throw new IOException("Handshake rejected: " + reason + " (expected version " + expectedVersion + ")");
    }

    if (response.type() != MessageType.WELCOME || !(response.payload() instanceof Payloads.Welcome welcome)) {
      throw new IOException("Invalid handshake response: " + response.type());
    }

    if (welcome.protocolVersion() != SocketProtocolConstants.PROTOCOL_VERSION) {
      throw new ProtocolMismatchException(
        "Unsupported protocol version: " + welcome.protocolVersion(),
        welcome.protocolVersion(),
        SocketProtocolConstants.PROTOCOL_VERSION
      );
    }

    this.serverLogLayout = welcome.logLayout();
  }

  private @Nullable Message<?> readMessageWithTimeout(final long timeoutMs) throws IOException {
    return TimedRead.read(
      this::readMessage,
      timeoutMs,
      "Handshake interrupted",
      this::disconnect,
      SocketProtocolConstants.HANDSHAKE_TIMEOUT_JOIN_MS
    );
  }

  private @Nullable Message<?> readMessage() throws IOException {
    final DataInputStream in = this.reader;
    if (in == null) {
      return null;
    }

    final byte[] data = FrameCodec.readFrame(in);
    if (data == null) {
      return null;
    }

    final String json = new String(data, StandardCharsets.UTF_8);
    final Message<?> message = this.serializer.deserialize(json);
    if (message == null) {
      throw new IOException("Invalid message payload");
    }
    return message;
  }

  private void writeMessage(final Message<?> message) throws IOException {
    final DataOutputStream out = this.writer;
    if (out == null) {
      return;
    }

    final byte[] json = this.serializer.serialize(message).getBytes(StandardCharsets.UTF_8);
    synchronized (this.writeLock) {
      FrameCodec.writeFrame(out, json);
    }
  }

  public <T extends MessagePayload> Message<T> createRequest(final MessageType type, final T payload) {
    return Message.<T>builder(type)
      .requestId(UUID.randomUUID())
      .payload(payload)
      .build();
  }

  private Message<?> awaitResponse(
    final CompletableFuture<Message<?>> future,
    final MessageType expectedResponseType,
    final long timeoutMs
  ) throws ExecutionException, TimeoutException, IOException {
    final Message<?> response;
    try {
      response = future.get(timeoutMs, TimeUnit.MILLISECONDS);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("Interrupted while waiting for response", e);
    }
    if (response.type() == MessageType.ERROR && response.payload() instanceof Payloads.Error(String error, String details)) {
      throw new IOException(details != null ? error + ": " + details : error);
    }
    if (response.type() != expectedResponseType) {
      throw new IOException("Unexpected response type: " + response.type() + " (expected: " + expectedResponseType + ")");
    }
    return response;
  }

  private IOException wrapResponseException(
    final MessageType expectedResponseType,
    final long timeoutMs,
    final Exception e
  ) {
    if (e instanceof ExecutionException executionException) {
      final Throwable cause = executionException.getCause();
      if (cause instanceof IOException ioe) {
        return ioe;
      }
      return new IOException("Failed to get response", cause);
    }
    return new IOException("Timeout waiting for response of type '" + expectedResponseType + "' after " + timeoutMs + "ms", e);
  }

  private void failPendingRequests(final IOException error) {
    this.pendingRequests.forEach((id, future) -> future.completeExceptionally(error));
    this.pendingRequests.clear();
  }

  private void closeResources() {
    if (this.writer != null) {
      try {
        this.writer.close();
      } catch (final IOException ignored) {
        LOGGER.debug("Failed to close socket writer", ignored);
      }
      this.writer = null;
    }
    if (this.reader != null) {
      try {
        this.reader.close();
      } catch (final IOException ignored) {
        LOGGER.debug("Failed to close socket reader", ignored);
      }
      this.reader = null;
    }
    if (this.socketChannel != null) {
      try {
        this.socketChannel.close();
      } catch (final IOException ignored) {
        LOGGER.debug("Failed to close socket channel", ignored);
      }
      this.socketChannel = null;
    }
    this.interactivityAvailable = false;
  }

  private static boolean requiresInteractivity(final MessageType type) {
    return switch (type) {
      case COMPLETION_REQUEST, SYNTAX_HIGHLIGHT_REQUEST, PARSE_REQUEST, COMMAND_EXECUTE -> true;
      default -> false;
    };
  }

}
