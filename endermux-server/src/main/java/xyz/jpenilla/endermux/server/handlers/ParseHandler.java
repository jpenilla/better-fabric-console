package xyz.jpenilla.endermux.server.handlers;

import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.jpenilla.endermux.protocol.MessageType;
import xyz.jpenilla.endermux.protocol.Payloads;
import xyz.jpenilla.endermux.server.api.ServerHooks;

@NullMarked
public final class ParseHandler implements MessageHandler<Payloads.ParseRequest> {

  private static final Logger LOGGER = LoggerFactory.getLogger(ParseHandler.class);

  private final ServerHooks hooks;

  public ParseHandler(final ServerHooks hooks) {
    this.hooks = hooks;
  }

  @Override
  public MessageType type() {
    return MessageType.PARSE_REQUEST;
  }

  @Override
  public Class<Payloads.ParseRequest> payloadType() {
    return Payloads.ParseRequest.class;
  }

  @Override
  public void handle(final Payloads.ParseRequest payload, final ResponseContext ctx) {
    if (!ctx.hasRequestId()) {
      ctx.error("Parse requests require a requestId");
      return;
    }

    try {
      final ServerHooks.CommandParser parser = this.hooks.parser();
      if (parser == null) {
        ctx.error("Parsing is not supported");
        return;
      }

      ctx.reply(parser.parse(payload.command(), payload.cursor()));

    } catch (final Exception e) {
      LOGGER.debug("Failed to parse command: {}", payload.command(), e);
      ctx.error("Failed to parse command", e.getMessage());
    }
  }
}
