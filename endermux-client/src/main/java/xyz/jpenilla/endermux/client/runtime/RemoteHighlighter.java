package xyz.jpenilla.endermux.client.runtime;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.jline.reader.Highlighter;
import org.jline.reader.LineReader;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.jpenilla.endermux.client.transport.SocketTransport;

@NullMarked
public final class RemoteHighlighter implements Highlighter {

  private static final int CACHE_SIZE = 64;
  private static final String ANSI_RESET = "\u001B[0m";
  private static final Logger LOGGER = LoggerFactory.getLogger(RemoteHighlighter.class);
  private static final ExecutorService REQUEST_EXECUTOR = Executors.newThreadPerTaskExecutor(
    Thread.ofVirtual().name("RemoteHighlighter-", 0).factory()
  );

  private final SocketTransport socketClient;
  private final Map<String, String> highlightCache;
  private final Set<String> inFlightRequests = Collections.synchronizedSet(new HashSet<>());
  private volatile String latestBuffer = "";

  public RemoteHighlighter(final SocketTransport socketClient) {
    this.socketClient = socketClient;
    this.highlightCache = Collections.synchronizedMap(new LinkedHashMap<>(CACHE_SIZE, 0.75f, true) {
      @Override
      protected boolean removeEldestEntry(final Map.Entry<String, String> eldest) {
        return size() > CACHE_SIZE;
      }
    });
  }

  @Override
  public AttributedString highlight(final LineReader reader, final String buffer) {
    this.latestBuffer = buffer;
    if (!this.socketClient.isConnected() || !this.socketClient.isInteractivityAvailable()) {
      return createUnhighlighted(buffer);
    }

    final String cached = this.highlightCache.get(buffer);
    if (cached != null) {
      return AttributedString.fromAnsi(cached);
    }

    this.requestHighlight(buffer);

    final PrefixHit prefixHit = this.longestPrefixHit(buffer);
    if (prefixHit == null) {
      return createUnhighlighted(buffer);
    }

    return AttributedString.fromAnsi(prefixHit.highlighted() + ANSI_RESET + prefixHit.suffix());
  }

  private static AttributedString createUnhighlighted(final String buffer) {
    final AttributedStringBuilder builder = new AttributedStringBuilder();
    builder.append(buffer, AttributedStyle.DEFAULT);
    return builder.toAttributedString();
  }

  @Override
  public void setErrorPattern(final java.util.regex.Pattern errorPattern) {
  }

  @Override
  public void setErrorIndex(final int errorIndex) {
  }

  private void requestHighlight(final String buffer) {
    if (!this.inFlightRequests.add(buffer)) {
      return;
    }

    REQUEST_EXECUTOR.execute(() -> {
      try {
        final String highlighted = this.socketClient.getSyntaxHighlight(buffer);
        this.highlightCache.put(buffer, highlighted);
        this.redrawIfRelevant(buffer);
      } catch (final IOException | InterruptedException e) {
        LOGGER.debug("Failed to request syntax highlight", e);
      } finally {
        this.inFlightRequests.remove(buffer);
      }
    });
  }

  private @Nullable PrefixHit longestPrefixHit(final String buffer) {
    for (int i = buffer.length() - 1; i > 0; i--) {
      final String prefix = buffer.substring(0, i);
      final String highlighted = this.highlightCache.get(prefix);
      if (highlighted != null) {
        return new PrefixHit(highlighted, buffer.substring(i));
      }
    }
    return null;
  }

  private void redrawIfRelevant(final String buffer) {
    final String latest = this.latestBuffer;
    if (!latest.equals(buffer) && !latest.startsWith(buffer)) {
      return;
    }
    try {
      TerminalOutput.redrawLineIfReading();
    } catch (final RuntimeException e) {
      LOGGER.debug("Failed to redraw line after async highlight", e);
    }
  }

  private record PrefixHit(String highlighted, String suffix) {
  }
}
