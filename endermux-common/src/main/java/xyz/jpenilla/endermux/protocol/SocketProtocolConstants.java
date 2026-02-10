package xyz.jpenilla.endermux.protocol;

import org.jspecify.annotations.NullMarked;

@NullMarked
public final class SocketProtocolConstants {

  private SocketProtocolConstants() {
  }

  public static final int PROTOCOL_VERSION = 10;

  public static final int MAX_FRAME_SIZE_BYTES = 1024 * 1024;

  public static final long HANDSHAKE_TIMEOUT_MS = 2000L;

  public static final long HANDSHAKE_TIMEOUT_JOIN_MS = 1000L;

  public static final long SYNTAX_HIGHLIGHT_TIMEOUT_MS = 1000L;

  public static final long COMPLETION_TIMEOUT_MS = 5000L;
}
