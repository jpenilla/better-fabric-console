package xyz.jpenilla.endermux.protocol;

import java.util.UUID;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class Message<T extends MessagePayload> {

  private final MessageType type;
  private final @Nullable String requestId;
  private final T payload;

  Message(final MessageType type, final @Nullable String requestId, final T payload) {
    this.type = type;
    this.requestId = requestId;
    this.payload = payload;
  }

  public MessageType type() {
    return this.type;
  }

  public @Nullable String requestId() {
    return this.requestId;
  }

  public T payload() {
    return this.payload;
  }

  public boolean hasRequestId() {
    return this.requestId != null;
  }

  public static <T extends MessagePayload> Builder<T> builder(final MessageType type) {
    return new Builder<>(type);
  }

  public static <T extends MessagePayload> Message<T> response(
    final String requestId,
    final MessageType type,
    final T payload
  ) {
    return new Message<>(type, requestId, payload);
  }

  public static <T extends MessagePayload> Message<T> unsolicited(final MessageType type, final T payload) {
    return new Message<>(type, null, payload);
  }

  public static final class Builder<T extends MessagePayload> {
    private final MessageType type;
    private @Nullable String requestId;
    private @Nullable T payload;

    private Builder(final MessageType type) {
      this.type = type;
    }

    public Builder<T> requestId(final String requestId) {
      this.requestId = requestId;
      return this;
    }

    public Builder<T> requestId(final UUID requestId) {
      this.requestId = requestId.toString();
      return this;
    }

    public Builder<T> payload(final T payload) {
      this.payload = payload;
      return this;
    }

    public Message<T> build() {
      if (this.payload == null) {
        throw new IllegalStateException("Message must have a payload");
      }
      return new Message<>(this.type, this.requestId, this.payload);
    }
  }
}
