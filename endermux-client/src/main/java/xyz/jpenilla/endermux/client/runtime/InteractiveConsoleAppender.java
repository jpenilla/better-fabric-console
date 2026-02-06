package xyz.jpenilla.endermux.client.runtime;

import java.io.Serializable;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Core;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@Plugin(
  name = "InteractiveConsoleAppender",
  category = Core.CATEGORY_NAME,
  elementType = Appender.ELEMENT_TYPE
)
@NullMarked
public final class InteractiveConsoleAppender extends AbstractAppender {
  private InteractiveConsoleAppender(
    final String name,
    final Filter filter,
    final Layout<? extends Serializable> layout
  ) {
    super(name, filter, layout != null ? layout : PatternLayout.createDefaultLayout(), true, Property.EMPTY_ARRAY);
  }

  @PluginFactory
  public static InteractiveConsoleAppender createAppender(
    @PluginAttribute("name") final @Nullable String name,
    @PluginElement("Filter") final @Nullable Filter filter,
    @PluginElement("Layout") final @Nullable Layout<? extends Serializable> layout
  ) {
    final String appenderName = name != null ? name : "InteractiveConsole";
    return new InteractiveConsoleAppender(appenderName, filter, layout);
  }

  @Override
  public void append(final LogEvent event) {
    TerminalOutput.write(this.getLayout().toSerializable(event).toString());
  }
}
