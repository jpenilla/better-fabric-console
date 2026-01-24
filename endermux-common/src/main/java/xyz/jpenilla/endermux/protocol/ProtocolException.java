package xyz.jpenilla.endermux.protocol;

import java.io.IOException;
import java.io.Serial;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class ProtocolException extends IOException {
  @Serial
  private static final long serialVersionUID = 6695715031533525201L;

  public ProtocolException(final String message) {
    super(message);
  }

  public ProtocolException(final String message, final Throwable cause) {
    super(message, cause);
  }
}
