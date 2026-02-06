package xyz.jpenilla.endermux.server.handlers;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.jpenilla.endermux.server.api.InteractiveConsoleHooks;
import xyz.jpenilla.endermux.protocol.MessageType;
import xyz.jpenilla.endermux.protocol.Payloads;
import java.util.function.Supplier;

@NullMarked
public final class CompletionHandler implements MessageHandler<Payloads.CompletionRequest> {

  private static final Logger LOGGER = LoggerFactory.getLogger(CompletionHandler.class);

  private final Supplier<@Nullable InteractiveConsoleHooks> hooks;

  public CompletionHandler(final Supplier<@Nullable InteractiveConsoleHooks> hooks) {
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
      final InteractiveConsoleHooks currentHooks = this.hooks.get();
      if (currentHooks == null) {
        ctx.error("Interactivity is currently unavailable");
        return;
      }
      final InteractiveConsoleHooks.CommandCompleter completer = currentHooks.completer();
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
