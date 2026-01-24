package xyz.jpenilla.endermux.server.api;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import xyz.jpenilla.endermux.protocol.LayoutConfig;
import xyz.jpenilla.endermux.protocol.Payloads;

@NullMarked
public interface ServerHooks {
  @Nullable CommandCompleter completer();

  @Nullable CommandParser parser();

  @Nullable CommandExecutor executor();

  @Nullable CommandHighlighter highlighter();

  ServerMetadata metadata();

  interface CommandCompleter {
    Payloads.CompletionResponse complete(String command, int cursor) throws Exception;
  }

  interface CommandParser {
    Payloads.ParseResponse parse(String command, int cursor) throws Exception;
  }

  interface CommandExecutor {
    Payloads.CommandResponse execute(String command) throws Exception;
  }

  interface CommandHighlighter {
    Payloads.SyntaxHighlightResponse highlight(String command) throws Exception;
  }

  interface ServerMetadata {
    LayoutConfig logLayout();
  }
}
