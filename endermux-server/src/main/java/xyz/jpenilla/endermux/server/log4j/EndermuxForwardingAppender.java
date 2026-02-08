package xyz.jpenilla.endermux.server.log4j;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Core;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginConfiguration;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.apache.logging.log4j.core.layout.PatternMatch;
import org.apache.logging.log4j.core.layout.PatternSelector;
import org.jspecify.annotations.Nullable;
import xyz.jpenilla.endermux.log4j.LoggerNamePatternSelector;
import xyz.jpenilla.endermux.protocol.LayoutConfig;

@Plugin(
  name = "EndermuxForwardingAppender",
  category = Core.CATEGORY_NAME,
  elementType = Appender.ELEMENT_TYPE
)
public final class EndermuxForwardingAppender extends AbstractAppender {

  public static @Nullable LogForwardingTarget TARGET = null;
  public static @Nullable EndermuxForwardingAppender INSTANCE = null;

  private final LayoutConfig logLayout;

  public EndermuxForwardingAppender(
    final String name,
    final Filter filter,
    final Layout<? extends Serializable> layout
  ) {
    super(name, filter, layout != null ? layout : PatternLayout.createDefaultLayout(), true, Property.EMPTY_ARRAY);
    this.logLayout = extractLayoutConfig(this.getLayout());
    INSTANCE = this;
  }

  @PluginFactory
  public static EndermuxForwardingAppender createAppender(
    @PluginAttribute("name") String name,
    @PluginElement("Filter") Filter filter,
    @PluginElement("Layout") Layout<? extends Serializable> layout,
    @PluginConfiguration Configuration configuration
  ) {
    if (layout == null) {
      layout = PatternLayout.createDefaultLayout(configuration);
    }
    return new EndermuxForwardingAppender(name, filter, layout);
  }

  public LayoutConfig logLayout() {
    return this.logLayout;
  }

  private static LayoutConfig extractLayoutConfig(final Layout<? extends Serializable> layout) {
    if (layout instanceof PatternLayout patternLayout) {
      final PatternSelector selector = patternSelector(patternLayout);
      final LoggerNamePatternSelector loggerNameSelector = selector instanceof LoggerNamePatternSelector
        ? (LoggerNamePatternSelector) selector
        : null;
      if (loggerNameSelector != null) {
        return layoutFromLoggerNameSelector(loggerNameSelector, patternLayout.getCharset().name());
      }
      if (selector != null) {
        throw new IllegalArgumentException("Unsupported PatternSelector type: " + selector.getClass().getName());
      }
      return LayoutConfig.pattern(
        patternLayout.getConversionPattern(),
        defaultFlags(),
        patternLayout.getCharset().name()
      );
    }
    throw new IllegalArgumentException("Unsupported layout type: " + layout.getClass().getName());
  }

  private static LayoutConfig layoutFromLoggerNameSelector(
    final LoggerNamePatternSelector selector,
    final @Nullable String charset
  ) {
    final PatternMatch[] matches = selector.properties();
    final List<LayoutConfig.Match> matchList = Arrays.stream(matches)
      .map(match -> new LayoutConfig.Match(match.getKey(), match.getPattern()))
      .toList();
    final LayoutConfig.SelectorConfig selectorConfig = new LayoutConfig.SelectorConfig(
      selector.defaultPattern(),
      matchList
    );
    final LayoutConfig.Flags flags = new LayoutConfig.Flags(
      selector.alwaysWriteExceptions(),
      selector.disableAnsi(),
      selector.noConsoleNoAnsi()
    );
    return LayoutConfig.loggerNameSelector(selectorConfig, flags, charset);
  }

  private static LayoutConfig.Flags defaultFlags() {
    return new LayoutConfig.Flags(true, false, false);
  }

  private static @Nullable PatternSelector patternSelector(final PatternLayout layout) {
    try {
      final Field field = PatternLayout.class.getDeclaredField("patternSelector");
      field.setAccessible(true);
      return (PatternSelector) field.get(layout);
    } catch (final ReflectiveOperationException | RuntimeException ignored) {
      return null;
    }
  }

  @Override
  public void append(final LogEvent event) {
    final LogForwardingTarget target = TARGET;
    if (target != null) {
      target.forward(event);
    }
  }

  public interface LogForwardingTarget {
    void forward(LogEvent event);
  }
}
