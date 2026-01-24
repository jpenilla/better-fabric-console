package xyz.jpenilla.endermux.client.transport;

import org.jspecify.annotations.NullMarked;
import xyz.jpenilla.endermux.protocol.Message;
import xyz.jpenilla.endermux.protocol.MessagePayload;

@NullMarked
public interface TransportMessageHandler {
  void handleMessage(Message<? extends MessagePayload> message);
}
