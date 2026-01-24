package xyz.jpenilla.endermux.server.handlers;

import java.util.HashMap;
import java.util.Map;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import xyz.jpenilla.endermux.protocol.MessagePayload;
import xyz.jpenilla.endermux.protocol.MessageType;

@NullMarked
public final class HandlerRegistry {

  private final Map<MessageType, MessageHandler<?>> handlers = new HashMap<>();

  public <T extends MessagePayload> void register(final MessageHandler<T> handler) {
    this.handlers.put(handler.type(), handler);
  }

  public @Nullable MessageHandler<?> get(final MessageType type) {
    return this.handlers.get(type);
  }

  public boolean hasHandler(final MessageType type) {
    return this.handlers.containsKey(type);
  }

  @SuppressWarnings("unchecked")
  public boolean handle(final MessageType type, final MessagePayload payload, final ResponseContext ctx) {
    final MessageHandler<?> handler = this.handlers.get(type);
    if (handler == null) {
      return false;
    }

    ((MessageHandler<MessagePayload>) handler).handle(payload, ctx);
    return true;
  }
}
