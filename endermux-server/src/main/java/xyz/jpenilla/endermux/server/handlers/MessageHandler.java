package xyz.jpenilla.endermux.server.handlers;

import org.jspecify.annotations.NullMarked;
import xyz.jpenilla.endermux.protocol.MessagePayload;
import xyz.jpenilla.endermux.protocol.MessageType;

@NullMarked
public interface MessageHandler<T extends MessagePayload> {

  MessageType type();

  Class<T> payloadType();

  void handle(T payload, ResponseContext ctx);
}
