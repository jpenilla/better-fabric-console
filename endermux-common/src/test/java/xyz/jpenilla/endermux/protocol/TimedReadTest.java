package xyz.jpenilla.endermux.protocol;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimedReadTest {

  @Test
  void returnsValueBeforeTimeout() throws Exception {
    final String value = TimedRead.read(() -> "ok", 500, "interrupted", null, 0);
    assertEquals("ok", value);
  }

  @Test
  void returnsNullOnTimeoutAndRunsCallback() throws Exception {
    final CountDownLatch releaseRead = new CountDownLatch(1);
    final AtomicBoolean timeoutCalled = new AtomicBoolean(false);

    final String value = TimedRead.read(
      () -> {
        try {
          releaseRead.await(2, TimeUnit.SECONDS);
        } catch (final InterruptedException e) {
          Thread.currentThread().interrupt();
        }
        return "late";
      },
      25,
      "interrupted",
      () -> {
        timeoutCalled.set(true);
        releaseRead.countDown();
      },
      100
    );

    assertNull(value);
    assertTrue(timeoutCalled.get());
  }

  @Test
  void propagatesIOException() {
    final IOException ex = assertThrows(IOException.class, () -> TimedRead.read(
      () -> {
        throw new IOException("boom");
      },
      500,
      "interrupted",
      null,
      0
    ));
    assertEquals("boom", ex.getMessage());
  }
}
