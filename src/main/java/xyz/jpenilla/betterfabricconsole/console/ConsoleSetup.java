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

import java.nio.file.Paths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.jline.reader.Completer;
import org.jline.reader.Highlighter;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.Parser;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import xyz.jpenilla.betterfabricconsole.configuration.Config;
import xyz.jpenilla.betterfabricconsole.remap.Remapper;
import xyz.jpenilla.betterfabricconsole.remap.RemappingRewriter;

@NullMarked
public final class ConsoleSetup {
  private ConsoleSetup() {
  }

  private static LineReader buildLineReader(
    final Completer completer,
    final Highlighter highlighter,
    final Parser parser
  ) {
    System.setProperty("org.jline.reader.support.parsedline", "true"); // to hide a warning message about the parser not supporting

    return LineReaderBuilder.builder()
      .appName("Dedicated Server")
      .variable(LineReader.HISTORY_FILE, Paths.get(".console_history"))
      .completer(completer)
      .highlighter(highlighter)
      .parser(parser)
      .completionMatcher(new MinecraftCompletionMatcher())
      .option(LineReader.Option.INSERT_TAB, false)
      .option(LineReader.Option.DISABLE_EVENT_EXPANSION, true)
      .option(LineReader.Option.COMPLETE_IN_WORD, true)
      .build();
  }

  public static ConsoleState init(
    final @Nullable Remapper remapper,
    final Config config
  ) {
    final DelegatingCompleter delegatingCompleter = new DelegatingCompleter();
    final DelegatingHighlighter delegatingHighlighter = new DelegatingHighlighter();
    final DelegatingParser delegatingParser = new DelegatingParser();
    final LineReader lineReader = buildLineReader(
      delegatingCompleter,
      delegatingHighlighter,
      delegatingParser
    );

    final ConsoleAppender consoleAppender = new ConsoleAppender(
      lineReader,
      config.logPattern(),
      remapper != null ? new RemappingRewriter(remapper) : null
    );
    consoleAppender.start();

    final Logger logger = (Logger) LogManager.getRootLogger();
    final LoggerContext loggerContext = (LoggerContext) LogManager.getContext(false);
    final LoggerConfig loggerConfig = loggerContext.getConfiguration().getLoggerConfig(logger.getName());

    // replace SysOut appender with ConsoleAppender
    loggerConfig.removeAppender("SysOut");
    loggerConfig.addAppender(consoleAppender, loggerConfig.getLevel(), null);
    loggerContext.updateLoggers();

    return new ConsoleState(lineReader, delegatingCompleter, delegatingHighlighter, delegatingParser);
  }
}
