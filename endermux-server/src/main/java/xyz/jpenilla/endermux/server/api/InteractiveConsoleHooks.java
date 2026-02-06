package xyz.jpenilla.endermux.server.api;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import xyz.jpenilla.endermux.protocol.Payloads;

@NullMarked
public final class InteractiveConsoleHooks {
  private final @Nullable CommandCompleter completer;
  private final @Nullable CommandParser parser;
  private final @Nullable CommandExecutor executor;
  private final @Nullable CommandHighlighter highlighter;

  private InteractiveConsoleHooks(
    final @Nullable CommandCompleter completer,
    final @Nullable CommandParser parser,
    final @Nullable CommandExecutor executor,
    final @Nullable CommandHighlighter highlighter
  ) {
    this.completer = completer;
    this.parser = parser;
    this.executor = executor;
    this.highlighter = highlighter;
  }

  public static Builder builder() {
    return new Builder();
  }

  public @Nullable CommandCompleter completer() {
    return this.completer;
  }

  public @Nullable CommandParser parser() {
    return this.parser;
  }

  public @Nullable CommandExecutor executor() {
    return this.executor;
  }

  public @Nullable CommandHighlighter highlighter() {
    return this.highlighter;
  }

  public interface CommandCompleter {
    Payloads.CompletionResponse complete(String command, int cursor) throws Exception;
  }

  public interface CommandParser {
    Payloads.ParseResponse parse(String command, int cursor) throws Exception;
  }

  public interface CommandExecutor {
    Payloads.CommandResponse execute(String command) throws Exception;
  }

  public interface CommandHighlighter {
    Payloads.SyntaxHighlightResponse highlight(String command) throws Exception;
  }

  public static final class Builder {
    private @Nullable CommandCompleter completer;
    private @Nullable CommandParser parser;
    private @Nullable CommandExecutor executor;
    private @Nullable CommandHighlighter highlighter;

    private Builder() {
    }

    public Builder completer(final @Nullable CommandCompleter completer) {
      this.completer = completer;
      return this;
    }

    public Builder parser(final @Nullable CommandParser parser) {
      this.parser = parser;
      return this;
    }

    public Builder executor(final @Nullable CommandExecutor executor) {
      this.executor = executor;
      return this;
    }

    public Builder highlighter(final @Nullable CommandHighlighter highlighter) {
      this.highlighter = highlighter;
      return this;
    }

    public InteractiveConsoleHooks build() {
      return new InteractiveConsoleHooks(
        this.completer,
        this.parser,
        this.executor,
        this.highlighter
      );
    }
  }
}
