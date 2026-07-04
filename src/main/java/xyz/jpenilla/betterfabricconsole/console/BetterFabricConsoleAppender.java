/*
 * This file is part of Better Fabric Console, licensed under the MIT License.
 *
 * Copyright (c) 2021-2024 Jason Penilla
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package xyz.jpenilla.betterfabricconsole.console;

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
import org.apache.logging.log4j.core.config.plugins.validation.constraints.Required;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.jline.reader.LineReader;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import xyz.jpenilla.betterfabricconsole.BetterFabricConsolePreLaunch;

@Plugin(name = BetterFabricConsoleAppender.PLUGIN_NAME, category = Core.CATEGORY_NAME, elementType = Appender.ELEMENT_TYPE, printObject = true)
@NullMarked
final class BetterFabricConsoleAppender extends AbstractAppender {
  public static final String PLUGIN_NAME = "BetterFabricConsoleAppender";

  private final LineReader lineReader;

  BetterFabricConsoleAppender(
    final String name,
    final @Nullable Filter filter,
    final Layout<? extends Serializable> layout
  ) {
    super(
      name,
      filter,
      layout,
      false,
      new Property[0]
    );
    this.lineReader = BetterFabricConsolePreLaunch.instance().consoleState().lineReader();
  }

  @Override
  public void append(final LogEvent event) {
    if (this.lineReader.isReading()) {
      this.lineReader.callWidget(LineReader.CLEAR);
    }

    this.lineReader.getTerminal().writer().print(this.getLayout().toSerializable(event).toString());

    if (this.lineReader.isReading()) {
      this.lineReader.callWidget(LineReader.REDRAW_LINE);
      this.lineReader.callWidget(LineReader.REDISPLAY);
    }
    this.lineReader.getTerminal().writer().flush();
  }

  @PluginFactory
  public static BetterFabricConsoleAppender createAppender(
    final @Required(message = "No name provided for BetterFabricConsoleAppender") @PluginAttribute("name") String name,
    final @PluginElement("Filter") Filter filter,
    @PluginElement("Layout") @Nullable Layout<? extends Serializable> layout
  ) {
    if (layout == null) {
      layout = PatternLayout.createDefaultLayout();
    }

    return new BetterFabricConsoleAppender(name, filter, layout);
  }
}
