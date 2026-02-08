package xyz.jpenilla.endermux.server.handlers;

import java.util.function.Supplier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.jpenilla.endermux.protocol.MessageType;
import xyz.jpenilla.endermux.protocol.Payloads;
import xyz.jpenilla.endermux.server.api.InteractiveConsoleHooks;

@NullMarked
public final class CommandHandler implements MessageHandler<Payloads.CommandExecute> {

  private static final Logger LOGGER = LoggerFactory.getLogger(CommandHandler.class);

  private final Supplier<@Nullable InteractiveConsoleHooks> hooks;

  public CommandHandler(final Supplier<@Nullable InteractiveConsoleHooks> hooks) {
    this.hooks = hooks;
  }

  @Override
  public MessageType type() {
    return MessageType.COMMAND_EXECUTE;
  }

  @Override
  public Class<Payloads.CommandExecute> payloadType() {
    return Payloads.CommandExecute.class;
  }

  @Override
  public void handle(final Payloads.CommandExecute payload, final ResponseContext ctx) {
    try {
      final InteractiveConsoleHooks currentHooks = this.hooks.get();
      if (currentHooks == null) {
        ctx.error("Interactivity is currently unavailable");
        return;
      }
      final InteractiveConsoleHooks.CommandExecutor executor = currentHooks.executor();
      if (executor == null) {
        ctx.error("Command execution is not supported");
        return;
      }

      executor.execute(payload.command());
    } catch (final Exception e) {
      LOGGER.warn("Failed to execute command: {}", payload.command(), e);
      ctx.error("Failed to execute command", e.getMessage());
    }
  }
}
