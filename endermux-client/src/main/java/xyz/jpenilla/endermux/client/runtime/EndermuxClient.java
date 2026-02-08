package xyz.jpenilla.endermux.client.runtime;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import static net.kyori.adventure.text.Component.text;

@NullMarked
public final class EndermuxClient {
  private static final long SOCKET_POLL_INTERVAL_MS = 500;
  private static final ComponentLogger LOGGER = ComponentLogger.logger(EndermuxClient.class);

  private final ExecutorService logExecutor = Executors.newSingleThreadExecutor(r -> {
    final Thread thread = new Thread(r, "LogOutput");
    thread.setDaemon(true);
    return thread;
  });

  private volatile boolean shutdownRequested;
  private @Nullable ExitReason exitReason;
  private @Nullable TerminalRuntimeContext terminalContext;
  private volatile @Nullable RemoteConsoleSession activeSession;

  public void run(final String socketPath) throws Exception {
    this.terminalContext = TerminalRuntimeContext.create();

    LOGGER.info(text()
      .append(text("Endermux", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD))
      .append(text(" Client ").decorate(TextDecoration.BOLD))
      .append(text("v" + getClass().getPackage().getImplementationVersion()))
      .build());

    try {
      this.registerSignalHandlers();
      int retryCount = 0;
      while (true) {
        if (this.shutdownRequested) {
          break;
        }

        if (!this.waitForSocket(Paths.get(socketPath))) {
          break;
        }

        if (this.shutdownRequested) {
          break;
        }

        final RemoteConsoleSession.SessionOutcome sessionOutcome = this.runSession(socketPath);
        if (sessionOutcome.disconnectReason() == RemoteConsoleSession.DisconnectReason.USER_EOF) {
          this.exitReason = ExitReason.USER_EOF;
        }
        if (sessionOutcome.stopClient()) {
          break;
        }

        if (sessionOutcome.connected()) {
          retryCount = 0;
        }

        retryCount++;
        final long backoffMs = this.retryBackoffMs(retryCount);

        LOGGER.info(text("Disconnected from server.", NamedTextColor.RED, TextDecoration.BOLD));
        if (!this.waitForBackoff(backoffMs)) {
          break;
        }
      }
    } finally {
      this.shutdown();
      this.printFarewellIfNeeded();
      final TerminalRuntimeContext context = this.terminalContext;
      if (context != null) {
        context.close();
      }
      this.terminalContext = null;
    }
  }

  private RemoteConsoleSession.SessionOutcome runSession(final String socketPath) {
    final TerminalRuntimeContext context = this.terminalContext;
    if (context == null) {
      throw new IllegalStateException("Terminal context is not initialized");
    }
    final RemoteConsoleSession session = new RemoteConsoleSession(
      socketPath,
      context,
      this.logExecutor,
      () -> this.shutdownRequested
    );
    this.activeSession = session;
    try {
      return session.run();
    } finally {
      this.activeSession = null;
    }
  }

  private void registerSignalHandlers() {
    final TerminalRuntimeContext context = this.terminalContext;
    if (context == null) {
      return;
    }
    context.registerInterruptHandler(() -> {
      final RemoteConsoleSession session = this.activeSession;
      if (session == null || !session.isConnected()) {
        this.exitReason = ExitReason.USER_INTERRUPT_WHILE_WAITING;
        this.shutdownRequested = true;
      }
    });
  }

  private boolean waitForSocket(final Path socketPath) {
    final Path resolvedSocketPath = socketPath.toAbsolutePath().normalize();
    final String displayPath = socketPath.toString();
    if (Files.exists(resolvedSocketPath)) {
      return true;
    }

    final Path parentDir = resolvedSocketPath.getParent();
    if (parentDir == null || !Files.isDirectory(parentDir)) {
      throw new IllegalArgumentException("Parent directory does not exist: " +
        (parentDir != null ? parentDir : resolvedSocketPath));
    }

    LOGGER.info(text()
      .content("Waiting for socket to exist: " + displayPath)
      .decorate(TextDecoration.ITALIC)
      .build());

    try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
      parentDir.register(watchService, StandardWatchEventKinds.ENTRY_CREATE);

      if (Files.exists(resolvedSocketPath)) {
        return true;
      }

      while (true) {
        if (this.shutdownRequested) {
          return false;
        }
        final WatchKey key;
        try {
          key = watchService.poll(SOCKET_POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);
        } catch (final InterruptedException e) {
          if (this.shutdownRequested) {
            return false;
          }
          LOGGER.debug("Interrupted while waiting for the socket to appear", e);
          continue;
        }
        if (key == null) {
          continue;
        }

        for (final WatchEvent<?> event : key.pollEvents()) {
          if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
            final Path created = parentDir.resolve((Path) event.context());
            if (created.equals(resolvedSocketPath)) {
              return true;
            }
          }
        }

        if (!key.reset()) {
          LOGGER.warn("File watching failed, falling back to polling");
          break;
        }
      }
    } catch (final IOException e) {
      LOGGER.warn("File watching unavailable ({}), falling back to polling", e.getMessage());
    }

    while (!Files.exists(resolvedSocketPath)) {
      if (this.shutdownRequested) {
        return false;
      }
      try {
        Thread.sleep(SOCKET_POLL_INTERVAL_MS);
      } catch (final InterruptedException e) {
        if (this.shutdownRequested) {
          return false;
        }
        LOGGER.debug("Interrupted while polling for socket availability", e);
      }
    }
    return true;
  }

  private boolean waitForBackoff(final long backoffMs) {
    if (backoffMs <= 0) {
      return true;
    }
    LOGGER.info("Reconnecting in {}...", formatBackoff(backoffMs));
    try {
      Thread.sleep(backoffMs);
      return true;
    } catch (final InterruptedException e) {
      if (this.shutdownRequested) {
        return false;
      }
      LOGGER.debug("Interrupted while sleeping for reconnect backoff", e);
      return true;
    }
  }

  private long retryBackoffMs(final int attempt) {
    return switch (attempt) {
      case 1 -> 0L;
      case 2 -> 500L;
      case 3 -> 1000L;
      case 4 -> 2000L;
      case 5 -> 3000L;
      case 6 -> 4000L;
      default -> 5000L;
    };
  }

  private static String formatBackoff(final long backoffMs) {
    if (backoffMs % 1000L == 0) {
      return backoffMs / 1000L + "s";
    }
    final double seconds = backoffMs / 1000.0;
    return String.format(Locale.ROOT, "%.1fs", seconds);
  }

  private void shutdown() {
    this.logExecutor.shutdown();
    try {
      if (!this.logExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
        this.logExecutor.shutdownNow();
      }
    } catch (final InterruptedException e) {
      this.logExecutor.shutdownNow();
      Thread.currentThread().interrupt();
    }

    final RemoteConsoleSession session = this.activeSession;
    if (session != null) {
      session.disconnect();
    }
  }

  private void printFarewellIfNeeded() {
    if (this.exitReason == null) {
      return;
    }
    LOGGER.info("Goodbye!");
  }

  private enum ExitReason {
    USER_EOF,
    USER_INTERRUPT_WHILE_WAITING
  }
}
