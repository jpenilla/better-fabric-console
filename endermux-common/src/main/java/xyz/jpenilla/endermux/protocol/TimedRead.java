package xyz.jpenilla.endermux.protocol;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class TimedRead {

  private TimedRead() {
  }

  public static <T> @Nullable T read(
    final ThrowingSupplier<T> supplier,
    final long timeoutMs,
    final String interruptMessage,
    final @Nullable Runnable onTimeout,
    final long timeoutJoinMs
  ) throws IOException {
    final AtomicReference<@Nullable T> result = new AtomicReference<>();
    final AtomicReference<@Nullable IOException> error = new AtomicReference<>();

    final Thread readerThread = Thread.ofVirtual().start(() -> {
      try {
        result.set(supplier.get());
      } catch (final IOException e) {
        error.set(e);
      }
    });

    try {
      readerThread.join(timeoutMs);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException(interruptMessage, e);
    }

    if (readerThread.isAlive()) {
      if (onTimeout != null) {
        onTimeout.run();
      }
      if (timeoutJoinMs > 0) {
        try {
          readerThread.join(timeoutJoinMs);
        } catch (final InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }
      return null;
    }

    final IOException err = error.get();
    if (err != null) {
      throw err;
    }

    return result.get();
  }

  public static <T> @Nullable T read(
    final ThrowingSupplier<T> supplier,
    final long timeoutMs,
    final String interruptMessage,
    final @Nullable Runnable onTimeout
  ) throws IOException {
    return read(supplier, timeoutMs, interruptMessage, onTimeout, 0L);
  }

  @FunctionalInterface
  public interface ThrowingSupplier<T> {
    T get() throws IOException;
  }
}
