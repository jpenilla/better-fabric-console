package xyz.jpenilla.endermux.protocol;

import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public record LayoutConfig(
  Type type,
  @Nullable String pattern,
  @Nullable SelectorConfig selector,
  Flags flags,
  @Nullable String charset
) {

  public enum Type {
    PATTERN,
    LOGGER_NAME_SELECTOR
  }

  public record SelectorConfig(String defaultPattern, List<Match> matches) {
  }

  public record Match(String key, String pattern) {
  }

  public record Flags(boolean alwaysWriteExceptions, boolean disableAnsi, boolean noConsoleNoAnsi) {
  }

  public static LayoutConfig pattern(final String pattern, final Flags flags, final @Nullable String charset) {
    return new LayoutConfig(Type.PATTERN, pattern, null, flags, charset);
  }

  public static LayoutConfig loggerNameSelector(
    final SelectorConfig selector,
    final Flags flags,
    final @Nullable String charset
  ) {
    return new LayoutConfig(Type.LOGGER_NAME_SELECTOR, selector.defaultPattern(), selector, flags, charset);
  }
}
