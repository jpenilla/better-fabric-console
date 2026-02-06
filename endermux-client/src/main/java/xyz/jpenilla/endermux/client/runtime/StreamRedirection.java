package xyz.jpenilla.endermux.client.runtime;

import java.io.PrintStream;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.io.IoBuilder;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class StreamRedirection {
  private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);

  private StreamRedirection() {
  }

  public static void install() {
    if (!INSTALLED.compareAndSet(false, true)) {
      return;
    }

    final PrintStream stdout = IoBuilder.forLogger("STDOUT")
      .setLevel(Level.INFO)
      .buildPrintStream();
    final PrintStream stderr = IoBuilder.forLogger("STDERR")
      .setLevel(Level.ERROR)
      .buildPrintStream();
    System.setOut(stdout);
    System.setErr(stderr);
  }
}
