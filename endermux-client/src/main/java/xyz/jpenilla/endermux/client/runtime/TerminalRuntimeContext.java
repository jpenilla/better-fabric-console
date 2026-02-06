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
import xyz.jpenilla.endermux.jline.TerminalDetection;

@NullMarked
final class TerminalRuntimeContext implements AutoCloseable {
  private static final Logger LOGGER = LoggerFactory.getLogger(TerminalRuntimeContext.class);
  private final boolean dumbTerminal;
  private final @Nullable Terminal terminal;

  private TerminalRuntimeContext(final boolean dumbTerminal, final @Nullable Terminal terminal) {
    this.dumbTerminal = dumbTerminal;
    this.terminal = terminal;
  }

  static TerminalRuntimeContext create() throws IOException {
    final boolean dumb = TerminalDetection.isDumb();
    final @Nullable Terminal terminal;
    if (dumb) {
      terminal = null;
    } else {
      terminal = TerminalBuilder.builder()
        .system(true)
        .build();
    }
    TerminalOutput.setTerminal(terminal);
    return new TerminalRuntimeContext(dumb, terminal);
  }

  boolean isDumbTerminal() {
    return this.dumbTerminal;
  }

  void registerInterruptHandler(final Runnable handler) {
    final Terminal term = this.terminal;
    if (term == null) {
      return;
    }
    term.handle(Terminal.Signal.INT, signal -> handler.run());
  }

  @Nullable LineReader createLineReader(final SocketTransport socketClient, final Highlighter highlighter) {
    if (this.dumbTerminal) {
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
