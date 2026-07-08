/*
 * This file is part of Better Fabric Console, licensed under the MIT License.
 *
 * Copyright (c) 2026 Jason Penilla
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
package xyz.jpenilla.betterfabricconsole.log4j;

import java.util.List;
import java.util.regex.Pattern;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.apache.logging.log4j.core.pattern.ConverterKeys;
import org.apache.logging.log4j.core.pattern.LogEventPatternConverter;
import org.apache.logging.log4j.core.pattern.PatternConverter;
import org.apache.logging.log4j.core.pattern.PatternFormatter;
import org.apache.logging.log4j.core.pattern.PatternParser;
import org.jspecify.annotations.NullMarked;

@Plugin(name = "stripAnsi", category = PatternConverter.CATEGORY)
@ConverterKeys({"stripAnsi"})
@NullMarked
public final class StripAnsiConverter extends LogEventPatternConverter {
  private static final Pattern ANSI_PATTERN = Pattern.compile("\\e\\[[\\d;]*[^\\d;]");

  private final List<PatternFormatter> formatters;

  private StripAnsiConverter(final List<PatternFormatter> formatters) {
    super("stripAnsi", null);
    this.formatters = formatters;
  }

  @Override
  public void format(final LogEvent event, final StringBuilder toAppendTo) {
    final int start = toAppendTo.length();
    for (final PatternFormatter formatter : this.formatters) {
      formatter.format(event, toAppendTo);
    }
    final String content = ANSI_PATTERN.matcher(toAppendTo.substring(start)).replaceAll("");

    toAppendTo.setLength(start);
    toAppendTo.append(content);
  }

  public static StripAnsiConverter newInstance(final Configuration config, final String[] options) {
    if (options.length != 1) {
      LOGGER.error("Incorrect number of options on stripAnsi. Expected exactly 1, received " + options.length);
      return null;
    }

    final PatternParser parser = PatternLayout.createPatternParser(config);
    final List<PatternFormatter> formatters = parser.parse(options[0]);
    return new StripAnsiConverter(formatters);
  }
}
