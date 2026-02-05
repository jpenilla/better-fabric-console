package xyz.jpenilla.endermux.client.transport;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class ProtocolMismatchException extends Exception {
  @java.io.Serial
  private static final long serialVersionUID = 1L;

  private final String reason;
  private final int expectedVersion;
  private final int actualVersion;

  public ProtocolMismatchException(final String reason, final int expectedVersion, final int actualVersion) {
    super(reason);
    this.reason = reason;
    this.expectedVersion = expectedVersion;
    this.actualVersion = actualVersion;
  }

  public @Nullable String reason() {
    return this.reason;
  }

  public int expectedVersion() {
    return this.expectedVersion;
  }

  public int actualVersion() {
    return this.actualVersion;
  }
}
