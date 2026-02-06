package xyz.jpenilla.endermux.jline;

import java.io.IOException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class TerminalModeDetection {
  private static final boolean CONSOLE_INPUT_AVAILABLE = System.console() != null;
  private static final TerminalMode MODE = detectMode();

  private TerminalModeDetection() {
  }

  private static TerminalMode detectMode() {
    if (!CONSOLE_INPUT_AVAILABLE) {
      return TerminalMode.DUMB;
    }

    try (Terminal terminal = TerminalBuilder.builder().system(true).dumb(false).build()) {
      return Terminal.TYPE_DUMB.equals(terminal.getType()) ? TerminalMode.DUMB : TerminalMode.INTERACTIVE;
    } catch (final IOException | IllegalStateException e) {
      return TerminalMode.DUMB;
    }
  }

  public static TerminalMode mode() {
    return MODE;
  }

  public static boolean isDumb() {
    return MODE == TerminalMode.DUMB;
  }

  public static boolean isInteractive() {
    return MODE == TerminalMode.INTERACTIVE;
  }

  public static boolean hasConsoleInput() {
    return CONSOLE_INPUT_AVAILABLE;
  }
}
