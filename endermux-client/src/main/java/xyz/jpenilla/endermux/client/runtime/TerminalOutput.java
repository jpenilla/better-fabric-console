package xyz.jpenilla.endermux.client.runtime;

import java.io.PrintStream;
import java.util.concurrent.atomic.AtomicReference;
import org.jline.reader.LineReader;
import org.jline.terminal.Terminal;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class TerminalOutput {
  private static final Object LOCK = new Object();
  private static final AtomicReference<@Nullable LineReader> LINE_READER = new AtomicReference<>();
  private static final AtomicReference<@Nullable Terminal> TERMINAL = new AtomicReference<>();
  private static final AtomicReference<@Nullable PrintStream> ORIGINAL_OUT = new AtomicReference<>();
  private static final AtomicReference<@Nullable PrintStream> ORIGINAL_ERR = new AtomicReference<>();

  private TerminalOutput() {
  }

  public static void captureOriginalStreams(final PrintStream out, final PrintStream err) {
    ORIGINAL_OUT.compareAndSet(null, out);
    ORIGINAL_ERR.compareAndSet(null, err);
  }

  public static void setTerminal(final @Nullable Terminal terminal) {
    TERMINAL.set(terminal);
  }

  public static void setLineReader(final @Nullable LineReader lineReader) {
    LINE_READER.set(lineReader);
  }

  public static void write(final String message) {
    synchronized (LOCK) {
      final @Nullable LineReader lineReader = LINE_READER.get();
      final boolean reading = lineReader != null && lineReader.isReading();
      if (reading) {
        lineReader.callWidget(LineReader.CLEAR);
      }

      final @Nullable Terminal terminal = lineReader != null ? lineReader.getTerminal() : TERMINAL.get();
      if (terminal != null) {
        terminal.writer().print(message);
        terminal.writer().flush();
      } else {
        originalErr().print(message);
        originalErr().flush();
      }

      if (reading) {
        lineReader.callWidget(LineReader.REDRAW_LINE);
        lineReader.callWidget(LineReader.REDISPLAY);
      }
    }
  }

  public static PrintStream originalOut() {
    final @Nullable PrintStream out = ORIGINAL_OUT.get();
    return out != null ? out : System.out;
  }

  public static PrintStream originalErr() {
    final @Nullable PrintStream err = ORIGINAL_ERR.get();
    return err != null ? err : System.err;
  }
}
