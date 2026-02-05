package xyz.jpenilla.endermux.server.handlers;

import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.jpenilla.endermux.protocol.MessageType;
import xyz.jpenilla.endermux.protocol.Payloads;
import xyz.jpenilla.endermux.server.api.ConsoleHooks;

@NullMarked
public final class CompletionHandler implements MessageHandler<Payloads.CompletionRequest> {

  private static final Logger LOGGER = LoggerFactory.getLogger(CompletionHandler.class);

  private final ConsoleHooks hooks;

  public CompletionHandler(final ConsoleHooks hooks) {
    this.hooks = hooks;
  }

  @Override
  public MessageType type() {
    return MessageType.COMPLETION_REQUEST;
  }

  @Override
  public Class<Payloads.CompletionRequest> payloadType() {
    return Payloads.CompletionRequest.class;
  }

  @Override
  public void handle(final Payloads.CompletionRequest payload, final ResponseContext ctx) {
    if (!ctx.hasRequestId()) {
      ctx.error("Completion requests require a requestId");
      return;
    }

    try {
      final ConsoleHooks.CommandCompleter completer = this.hooks.completer();
      if (completer == null) {
        ctx.error("Completions are not supported");
        return;
      }

      ctx.reply(completer.complete(payload.command(), payload.cursor()));

    } catch (final Exception e) {
      LOGGER.debug("Failed to get completions for command: {}", payload.command(), e);
      ctx.error("Failed to get completions", e.getMessage());
    }
  }
}
