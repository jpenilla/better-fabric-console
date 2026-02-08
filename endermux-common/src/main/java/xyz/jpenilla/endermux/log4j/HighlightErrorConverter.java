/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017 Minecrell <https://github.com/Minecrell>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package xyz.jpenilla.endermux.log4j;

import java.util.List;
import net.kyori.ansi.ColorLevel;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.apache.logging.log4j.core.pattern.ConverterKeys;
import org.apache.logging.log4j.core.pattern.HighlightConverter;
import org.apache.logging.log4j.core.pattern.LogEventPatternConverter;
import org.apache.logging.log4j.core.pattern.PatternConverter;
import org.apache.logging.log4j.core.pattern.PatternFormatter;
import org.apache.logging.log4j.core.pattern.PatternParser;
import org.apache.logging.log4j.util.PerformanceSensitive;
import org.jspecify.annotations.Nullable;

/**
 * A simplified version of {@link HighlightConverter} that detects
 * if ANSI escape codes can be used
 * to highlight errors and warnings in the console.
 *
 * <p>If configured, it will mark all logged errors with a red color and all
 * warnings with a yellow color.</p>
 *
 * <p><b>Example usage:</b> {@code %EndermuxHighlightError{%level: %message}}</p>
 *
 * <p>Endermux version differs from TCA: Uses Kyori ANSI for color support detection.</p>
 */
@Plugin(name = "EndermuxHighlightError", category = PatternConverter.CATEGORY)
@ConverterKeys({ "EndermuxHighlightError" })
@PerformanceSensitive("allocation")
public final class HighlightErrorConverter extends LogEventPatternConverter {

    private static final String ANSI_RESET = "\u001B[m";
    private static final String ANSI_ERROR = "\u001B[31;1m"; // Bold Red
    private static final String ANSI_WARN = "\u001B[33;1m"; // Bold Yellow

    private final List<PatternFormatter> formatters;
    private final boolean ansi;

  /**
     * Construct the converter.
     *
     * @param formatters The pattern formatters to generate the text to highlight
     */
    protected HighlightErrorConverter(List<PatternFormatter> formatters) {
        super("EndermuxHighlightError", null);
        this.formatters = formatters;
        this.ansi = ColorLevel.compute() != ColorLevel.NONE;
    }

    @Override
    public void format(LogEvent event, StringBuilder toAppendTo) {
        if (this.ansi) {
            Level level = event.getLevel();
            if (level.isMoreSpecificThan(Level.ERROR)) {
                format(ANSI_ERROR, event, toAppendTo);
                return;
            } else if (level.isMoreSpecificThan(Level.WARN)) {
                format(ANSI_WARN, event, toAppendTo);
                return;
            }
        }

        //noinspection ForLoopReplaceableByForEach
        for (int i = 0, size = formatters.size(); i < size; i++) {
            formatters.get(i).format(event, toAppendTo);
        }
    }

    private void format(String style, LogEvent event, StringBuilder toAppendTo) {
        int start = toAppendTo.length();
        toAppendTo.append(style);
        int end = toAppendTo.length();

        //noinspection ForLoopReplaceableByForEach
        for (int i = 0, size = formatters.size(); i < size; i++) {
            formatters.get(i).format(event, toAppendTo);
        }

        if (toAppendTo.length() == end) {
            // No content so we don't need to append the ANSI escape code
            toAppendTo.setLength(start);
        } else {
            // Append reset code after the line
            toAppendTo.append(ANSI_RESET);
        }
    }

    @Override
    public boolean handlesThrowable() {
        for (final PatternFormatter formatter : formatters) {
            if (formatter.handlesThrowable()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gets a new instance of the {@link HighlightErrorConverter} with the
     * specified options.
     *
     * @param config The current configuration
     * @param options The pattern options
     * @return The new instance
     */
    public static @Nullable HighlightErrorConverter newInstance(Configuration config, String[] options) {
        if (options.length != 1) {
            LOGGER.error("Incorrect number of options on EndermuxHighlightError. Expected 1 received " + options.length);
            return null;
        }
        if (options[0] == null) {
            LOGGER.error("No pattern supplied on EndermuxHighlightError");
            return null;
        }

        PatternParser parser = PatternLayout.createPatternParser(config);
        List<PatternFormatter> formatters = parser.parse(options[0]);
        return new HighlightErrorConverter(formatters);
    }

}
