package xyz.jpenilla.endermux.protocol;

import org.jspecify.annotations.NullMarked;

/**
 * Represents the state of a socket connection.
 * Used by both client and server to track connection lifecycle.
 */
@NullMarked
public enum ConnectionState {
  /**
   * Connection is being established.
   */
  CONNECTING,

  /**
   * Connection is established and ready for communication.
   */
  CONNECTED,

  /**
   * Connection is in the process of being closed.
   */
  DISCONNECTING,

  /**
   * Connection has been closed.
   */
  DISCONNECTED
}
