package xyz.jpenilla.endermux.server.handlers;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import xyz.jpenilla.endermux.protocol.MessagePayload;

@NullMarked
public interface ResponseContext {

  @Nullable String requestId();

  default boolean hasRequestId() {
    return requestId() != null;
  }

  void reply(MessagePayload payload);

  void error(String message);

  void error(String message, @Nullable String details);
}
