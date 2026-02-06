package xyz.jpenilla.endermux.client.runtime;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jline.reader.Highlighter;
import org.jline.reader.LineReader;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import xyz.jpenilla.endermux.client.transport.SocketTransport;

@NullMarked
public final class RemoteHighlighter implements Highlighter {

  private static final int CACHE_SIZE = 64;
  private static final Logger LOGGER = LogManager.getLogger();

  private final SocketTransport socketClient;
  private final Map<String, String> highlightCache;

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
    if (!this.socketClient.isConnected() || !this.socketClient.isInteractivityAvailable()) {
      return createUnhighlighted(buffer);
    }

    final @Nullable String cached = this.highlightCache.get(buffer);
    if (cached != null) {
      return AttributedString.fromAnsi(cached);
    }

    try {
      final String highlighted = this.socketClient.getSyntaxHighlight(buffer);
      this.highlightCache.put(buffer, highlighted);
      return AttributedString.fromAnsi(highlighted);
    } catch (final IOException | InterruptedException e) {
      LOGGER.debug("Failed to request syntax highlight", e);
      return createUnhighlighted(buffer);
    }
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
}
