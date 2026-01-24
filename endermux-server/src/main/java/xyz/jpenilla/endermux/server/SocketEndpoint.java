package xyz.jpenilla.endermux.server;

import java.io.IOException;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import xyz.jpenilla.endermux.protocol.ConnectionState;
import xyz.jpenilla.endermux.protocol.Message;

@NullMarked
public interface SocketEndpoint {

  Optional<Message<?>> readMessage() throws IOException;

  boolean writeMessage(Message<?> message) throws IOException;

  void close();

  ConnectionState getState();

  boolean isOpen();

  @Nullable String getRemoteAddress();
}
