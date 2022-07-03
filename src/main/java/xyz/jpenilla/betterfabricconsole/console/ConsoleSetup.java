/*
 * This file is part of Better Fabric Console, licensed under the MIT License.
 *
 * Copyright (c) 2021-2022 Jason Penilla
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

import java.nio.file.Paths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.framework.qual.DefaultQualifier;
import org.jline.reader.Completer;
import org.jline.reader.Highlighter;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import xyz.jpenilla.betterfabricconsole.remap.Remapper;
import xyz.jpenilla.betterfabricconsole.remap.RemappingRewriter;

@DefaultQualifier(NonNull.class)
public final class ConsoleSetup {
  private ConsoleSetup() {
  }

  public static LineReader buildLineReader(
    final Completer completer,
    final Highlighter highlighter
  ) {
    return LineReaderBuilder.builder()
      .appName("Dedicated Server")
      .variable(LineReader.HISTORY_FILE, Paths.get(".console_history"))
      .completer(completer)
      .highlighter(highlighter)
      .option(LineReader.Option.INSERT_TAB, false)
      .option(LineReader.Option.DISABLE_EVENT_EXPANSION, true)
      .option(LineReader.Option.COMPLETE_IN_WORD, true)
      .build();
  }

  public static void init(final LineReader lineReader, final @Nullable Remapper remapper, final String logPattern) {
    final ConsoleAppender consoleAppender = new ConsoleAppender(lineReader, logPattern);
    consoleAppender.start();

    final Logger logger = (Logger) LogManager.getRootLogger();
    final LoggerContext loggerContext = (LoggerContext) LogManager.getContext(false);
    final LoggerConfig loggerConfig = loggerContext.getConfiguration().getLoggerConfig(logger.getName());

    if (remapper != null) {
      consoleAppender.installRewriter(new RemappingRewriter(remapper));
    }

    // replace SysOut appender with ConsoleAppender
    loggerConfig.removeAppender("SysOut");
    loggerConfig.addAppender(consoleAppender, loggerConfig.getLevel(), null);
    loggerContext.updateLoggers();
  }
}
