package xyz.jpenilla.endermux.server.handlers;

import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.jpenilla.endermux.protocol.MessageType;
import xyz.jpenilla.endermux.protocol.Payloads;
import xyz.jpenilla.endermux.server.api.ConsoleHooks;

@NullMarked
public final class SyntaxHighlightHandler implements MessageHandler<Payloads.SyntaxHighlightRequest> {

  private static final Logger LOGGER = LoggerFactory.getLogger(SyntaxHighlightHandler.class);

  private final ConsoleHooks hooks;

  public SyntaxHighlightHandler(final ConsoleHooks hooks) {
    this.hooks = hooks;
  }

  @Override
  public MessageType type() {
    return MessageType.SYNTAX_HIGHLIGHT_REQUEST;
  }

  @Override
  public Class<Payloads.SyntaxHighlightRequest> payloadType() {
    return Payloads.SyntaxHighlightRequest.class;
  }

  @Override
  public void handle(final Payloads.SyntaxHighlightRequest payload, final ResponseContext ctx) {
    if (!ctx.hasRequestId()) {
      ctx.error("Syntax highlight requests require a requestId");
      return;
    }

    try {
      final ConsoleHooks.CommandHighlighter highlighter = this.hooks.highlighter();
      if (highlighter == null) {
        ctx.error("Syntax highlighting is not supported");
        return;
      }

      ctx.reply(highlighter.highlight(payload.command()));
    } catch (final Exception e) {
      LOGGER.debug("Failed to highlight command: {}", payload.command(), e);
      ctx.error("Failed to highlight command: " + e.getMessage());
    }
  }
}
