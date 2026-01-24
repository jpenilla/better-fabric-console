package xyz.jpenilla.endermux.jline;

import java.io.IOException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class TerminalDetection {
  private static final @Nullable Terminal TERMINAL = detectTerminal();

  private TerminalDetection() {
  }

  private static @Nullable Terminal detectTerminal() {
    try {
      return TerminalBuilder.builder().dumb(false).build();
    } catch (final IOException | IllegalStateException e) {
      return null;
    }
  }

  public static boolean isDumb() {
    return TERMINAL == null;
  }
}
