package xyz.jpenilla.endermux.client.runtime;

import java.nio.charset.Charset;
import java.util.List;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.apache.logging.log4j.core.layout.PatternMatch;
import org.jspecify.annotations.NullMarked;
import xyz.jpenilla.endermux.log4j.LoggerNamePatternSelector;
import xyz.jpenilla.endermux.protocol.LayoutConfig;

@NullMarked
final class LayoutConfigLayoutBuilder {
  private LayoutConfigLayoutBuilder() {
  }

  static PatternLayout toPatternLayout(final LayoutConfig config) {
    final PatternLayout.Builder builder = PatternLayout.newBuilder();
    final String charsetName = config.charset();
    if (charsetName != null) {
      builder.withCharset(Charset.forName(charsetName));
    }
    final LayoutConfig.Flags flags = config.flags();
    builder
      .withAlwaysWriteExceptions(flags.alwaysWriteExceptions())
      .withDisableAnsi(flags.disableAnsi())
      .withNoConsoleNoAnsi(flags.noConsoleNoAnsi());
    return switch (config.type()) {
      case PATTERN -> builder.withPattern(requiredPattern(config)).build();
      case LOGGER_NAME_SELECTOR -> builder.withPatternSelector(buildSelector(config)).build();
    };
  }

  private static String requiredPattern(final LayoutConfig config) {
    final String pattern = config.pattern();
    if (pattern == null) {
      throw new IllegalArgumentException("Pattern layout requires a pattern");
    }
    return transformPattern(pattern);
  }

  private static LoggerNamePatternSelector buildSelector(final LayoutConfig config) {
    final LayoutConfig.SelectorConfig selector = config.selector();
    if (selector == null) {
      throw new IllegalArgumentException("Logger name selector layout requires selector config");
    }
    final List<LayoutConfig.Match> matches = selector.matches();
    final PatternMatch[] patternMatches = new PatternMatch[matches.size()];
    for (int i = 0; i < matches.size(); i++) {
      final LayoutConfig.Match match = matches.get(i);
      patternMatches[i] = new PatternMatch(match.key(), transformPattern(match.pattern()));
    }
    final LayoutConfig.Flags flags = config.flags();
    return LoggerNamePatternSelector.createSelector(
      transformPattern(selector.defaultPattern()),
      patternMatches,
      flags.alwaysWriteExceptions(),
      flags.disableAnsi(),
      flags.noConsoleNoAnsi(),
      null
    );
  }

  private static String transformPattern(final String pattern) {
    return pattern.replace("%highlightError{", "%EndermuxHighlightError{");
  }
}
