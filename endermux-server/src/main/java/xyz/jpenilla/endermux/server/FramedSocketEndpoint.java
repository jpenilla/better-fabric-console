package xyz.jpenilla.endermux.server;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.jpenilla.endermux.protocol.ConnectionState;
import xyz.jpenilla.endermux.protocol.FrameCodec;
import xyz.jpenilla.endermux.protocol.Message;
import xyz.jpenilla.endermux.protocol.MessageSerializer;
import xyz.jpenilla.endermux.protocol.ProtocolException;

@NullMarked
public final class FramedSocketEndpoint implements SocketEndpoint {

  private static final Logger LOGGER = LoggerFactory.getLogger(FramedSocketEndpoint.class);

  private final SocketChannel socketChannel;
  private final MessageSerializer serializer;
  private final DataInputStream reader;
  private final DataOutputStream writer;
  private final java.util.concurrent.atomic.AtomicReference<ConnectionState> state;
  private final @Nullable String remoteAddress;

  public FramedSocketEndpoint(final SocketChannel socketChannel, final MessageSerializer serializer) throws IOException {
    this.socketChannel = socketChannel;
    this.serializer = serializer;
    this.state = new java.util.concurrent.atomic.AtomicReference<>(ConnectionState.CONNECTING);

    this.reader = new DataInputStream(new BufferedInputStream(Channels.newInputStream(socketChannel)));
    this.writer = new DataOutputStream(new BufferedOutputStream(Channels.newOutputStream(socketChannel)));

    String addr = null;
    try {
      addr = socketChannel.getRemoteAddress().toString();
    } catch (final IOException e) {
    }
    this.remoteAddress = addr;

    this.state.set(ConnectionState.CONNECTED);
  }

  @Override
  public Optional<Message<?>> readMessage() throws IOException {
    final byte[] data = FrameCodec.readFrame(this.reader);
    if (data == null) {
      return Optional.empty();
    }

    final String json = new String(data, StandardCharsets.UTF_8);
    final Message<?> message = this.serializer.deserialize(json);
    if (message == null) {
      LOGGER.warn("Received invalid message from client: {}", json.substring(0, Math.min(100, json.length())));
      throw new ProtocolException("Invalid message payload");
    }
    return Optional.of(message);
  }

  @Override
  public boolean writeMessage(final Message<?> message) throws IOException {
    if (!this.isOpen()) {
      return false;
    }

    final byte[] json = this.serializer.serialize(message).getBytes(StandardCharsets.UTF_8);
    FrameCodec.writeFrame(this.writer, json);
    return true;
  }

  @Override
  public void close() {
    if (this.state.compareAndSet(ConnectionState.CONNECTED, ConnectionState.DISCONNECTING)
      || this.state.compareAndSet(ConnectionState.CONNECTING, ConnectionState.DISCONNECTING)) {
      try {
        this.writer.close();
        this.reader.close();
        this.socketChannel.close();
      } catch (final IOException e) {
        LOGGER.debug("Error closing socket connection", e);
      } finally {
        this.state.set(ConnectionState.DISCONNECTED);
      }
    }
  }

  @Override
  public ConnectionState getState() {
    return this.state.get();
  }

  @Override
  public boolean isOpen() {
    return this.state.get() == ConnectionState.CONNECTED && this.socketChannel.isOpen();
  }

  @Override
  public @Nullable String getRemoteAddress() {
    return this.remoteAddress;
  }

}
