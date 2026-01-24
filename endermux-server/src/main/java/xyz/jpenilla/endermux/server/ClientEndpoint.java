package xyz.jpenilla.endermux.server;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.jpenilla.endermux.protocol.Message;
import xyz.jpenilla.endermux.protocol.MessageSerializer;
import xyz.jpenilla.endermux.protocol.ProtocolException;
import xyz.jpenilla.endermux.protocol.SocketProtocolConstants;
import xyz.jpenilla.endermux.protocol.TimedRead;

@NullMarked
public final class ClientEndpoint implements AutoCloseable {

  private static final Logger LOGGER = LoggerFactory.getLogger(ClientEndpoint.class);

  private final SocketEndpoint connection;
  private final BlockingQueue<Message<?>> outboundQueue = new LinkedBlockingQueue<>();
  private final AtomicBoolean writerStarted = new AtomicBoolean(false);
  private volatile Consumer<Message<?>> messageHandler;
  private volatile Runnable onDisconnect;
  private volatile boolean running = false;

  public ClientEndpoint(
    final SocketChannel socketChannel,
    final MessageSerializer serializer
  ) throws IOException {
    this.connection = new FramedSocketEndpoint(socketChannel, serializer);
  }

  public void start(final Consumer<Message<?>> messageHandler, final Runnable onDisconnect) {
    if (this.running) {
      throw new IllegalStateException("Connection already started");
    }
    this.running = true;
    this.messageHandler = Objects.requireNonNull(messageHandler, "messageHandler");
    this.onDisconnect = Objects.requireNonNull(onDisconnect, "onDisconnect");
    this.startWriter();
  }

  public void run() {
    if (this.messageHandler == null || this.onDisconnect == null) {
      throw new IllegalStateException("Must call start() before run()");
    }

    try {
      while (this.running && this.connection.isOpen()) {
        final Optional<Message<?>> message = this.connection.readMessage();
        if (message.isEmpty()) {
          break;
        }

        this.messageHandler.accept(message.get());
      }
    } catch (final ProtocolException e) {
      if (this.running) {
        LOGGER.debug("Protocol error from client", e);
      }
    } catch (final IOException e) {
      if (this.running) {
        LOGGER.debug("I/O error reading from client", e);
      }
    } finally {
      this.close();
    }
  }

  public boolean send(final Message<?> message) {
    if (!this.running) {
      return false;
    }
    return this.outboundQueue.offer(message);
  }

  public @Nullable Message<?> readInitialMessage(final long timeoutMs) throws IOException {
    final Message<?> message = this.readWithTimeout(timeoutMs);
    if (message == null) {
      throw new IOException("Connection closed before handshake");
    }
    return message;
  }

  public boolean isOpen() {
    return this.running && this.connection.isOpen();
  }

  @Override
  public void close() {
    this.shutdown();
  }

  private void startWriter() {
    if (!this.writerStarted.compareAndSet(false, true)) {
      return;
    }
    Thread.ofVirtual()
      .name("SocketWriter")
      .start(this::runWriterLoop);
  }

  private void runWriterLoop() {
    try {
      while (this.running && this.connection.isOpen()) {
        final Message<?> message = this.outboundQueue.poll(200, TimeUnit.MILLISECONDS);
        if (message == null) {
          continue;
        }
        try {
          this.connection.writeMessage(message);
        } catch (final IOException e) {
          LOGGER.debug("Failed to send message to client", e);
          break;
        }
      }
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
    } finally {
      this.shutdown();
    }
  }

  private @Nullable Message<?> readWithTimeout(final long timeoutMs) throws IOException {
    return TimedRead.read(
      () -> {
        try {
          return this.connection.readMessage().orElse(null);
        } catch (final ProtocolException e) {
          throw new IOException("Invalid handshake message", e);
        }
      },
      timeoutMs,
      "Handshake interrupted",
      this.connection::close,
      SocketProtocolConstants.HANDSHAKE_TIMEOUT_JOIN_MS
    );
  }

  private void shutdown() {
    if (this.running) {
      this.running = false;
      this.outboundQueue.clear();
      this.connection.close();
      final Runnable callback = this.onDisconnect;
      if (callback != null) {
        callback.run();
      }
    }
  }
}
