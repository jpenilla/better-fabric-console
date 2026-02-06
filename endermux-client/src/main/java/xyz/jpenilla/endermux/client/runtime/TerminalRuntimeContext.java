package xyz.jpenilla.endermux.client.runtime;

import java.io.IOException;
import java.io.InterruptedIOException;
import org.jline.reader.Highlighter;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.jpenilla.endermux.client.completer.RemoteCommandCompleter;
import xyz.jpenilla.endermux.client.parser.RemoteParser;
import xyz.jpenilla.endermux.client.transport.SocketTransport;
import xyz.jpenilla.endermux.jline.MinecraftCompletionMatcher;
import xyz.jpenilla.endermux.jline.TerminalMode;
import xyz.jpenilla.endermux.jline.TerminalModeDetection;

@NullMarked
final class TerminalRuntimeContext implements AutoCloseable {
  private static final Logger LOGGER = LoggerFactory.getLogger(TerminalRuntimeContext.class);
  private final TerminalMode mode;
  private final boolean consoleInputAvailable;
  private final @Nullable Terminal terminal;

  private TerminalRuntimeContext(
    final TerminalMode mode,
    final boolean consoleInputAvailable,
    final @Nullable Terminal terminal
  ) {
    this.mode = mode;
    this.consoleInputAvailable = consoleInputAvailable;
    this.terminal = terminal;
  }

  static TerminalRuntimeContext create() {
    final boolean consoleInputAvailable = TerminalModeDetection.hasConsoleInput();
    final TerminalMode detectedMode = TerminalModeDetection.mode();
    if (detectedMode == TerminalMode.DUMB) {
      TerminalOutput.setTerminal(null);
      return new TerminalRuntimeContext(TerminalMode.DUMB, consoleInputAvailable, null);
    }

    try {
      final Terminal terminal = TerminalBuilder.builder()
        .system(true)
        .dumb(false)
        .build();
      TerminalOutput.setTerminal(terminal);
      return new TerminalRuntimeContext(TerminalMode.INTERACTIVE, consoleInputAvailable, terminal);
    } catch (final IOException | IllegalStateException e) {
      LOGGER.debug("Falling back to dumb mode after interactive terminal setup failure", e);
      TerminalOutput.setTerminal(null);
      return new TerminalRuntimeContext(TerminalMode.DUMB, consoleInputAvailable, null);
    }
  }

  boolean isDumbTerminal() {
    return this.mode == TerminalMode.DUMB;
  }

  boolean hasConsoleInput() {
    return this.consoleInputAvailable;
  }

  void registerInterruptHandler(final Runnable handler) {
    final Terminal term = this.terminal;
    if (term == null) {
      return;
    }
    term.handle(Terminal.Signal.INT, signal -> handler.run());
  }

  @Nullable LineReader createLineReader(final SocketTransport socketClient, final Highlighter highlighter) {
    if (this.mode == TerminalMode.DUMB) {
      return null;
    }
    return LineReaderBuilder.builder()
      .appName("Endermux Client")
      .terminal(this.terminal)
      .completer(new RemoteCommandCompleter(socketClient))
      .highlighter(highlighter)
      .parser(new RemoteParser(socketClient))
      .completionMatcher(new MinecraftCompletionMatcher())
      .option(LineReader.Option.INSERT_TAB, false)
      .option(LineReader.Option.DISABLE_EVENT_EXPANSION, true)
      .option(LineReader.Option.COMPLETE_IN_WORD, true)
      .build();
  }

  void interruptActiveReader(final @Nullable LineReader reader) {
    final Terminal term = this.terminal;
    if (term != null && reader != null && reader.isReading()) {
      term.raise(Terminal.Signal.INT);
    }
  }

  @Override
  public void close() {
    final Terminal term = this.terminal;
    if (term != null) {
      // Clear the interrupt flag before closing. JLine close paths may spawn commands
      // and can fail with InterruptedIOException if the thread remains interrupted.
      if (Thread.currentThread().isInterrupted()) {
        Thread.interrupted();
      }
      try {
        term.close();
      } catch (final InterruptedIOException e) {
        LOGGER.debug("Interrupted while closing terminal", e);
      } catch (final IOException e) {
        LOGGER.warn("Failed to close terminal cleanly: {}", e.getMessage(), e);
      }
    }
    TerminalOutput.setLineReader(null);
    TerminalOutput.setTerminal(null);
  }
}
