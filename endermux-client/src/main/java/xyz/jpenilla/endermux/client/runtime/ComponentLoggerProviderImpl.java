package xyz.jpenilla.endermux.client.runtime;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.kyori.adventure.text.logger.slf4j.ComponentLoggerProvider;
import net.kyori.adventure.text.serializer.ansi.ANSIComponentSerializer;
import org.jspecify.annotations.NullMarked;
import org.slf4j.LoggerFactory;

@NullMarked
public final class ComponentLoggerProviderImpl implements ComponentLoggerProvider {
  private static final ANSIComponentSerializer ANSI = ANSIComponentSerializer.builder().build();

  @Override
  public ComponentLogger logger(final LoggerHelper helper, final String name) {
    return helper.delegating(LoggerFactory.getLogger(name), this::serialize);
  }

  private String serialize(final Component message) {
    return ANSI.serialize(message);
  }
}
