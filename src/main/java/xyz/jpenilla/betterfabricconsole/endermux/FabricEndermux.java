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
package xyz.jpenilla.betterfabricconsole.endermux;

import java.nio.file.Path;
import net.minecraft.server.dedicated.DedicatedServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import xyz.jpenilla.betterfabricconsole.configuration.Config;
import xyz.jpenilla.betterfabricconsole.console.ConsoleState;
import xyz.jpenilla.endermux.log4j.EndermuxForwardingAppender;
import xyz.jpenilla.endermux.server.EndermuxServer;
import xyz.jpenilla.endermux.server.api.InteractiveConsoleHooks;
import xyz.jpenilla.endermux.server.log.RemoteLogForwarder;

@NullMarked
public final class FabricEndermux {
  private static final String LOG_FORWARDER_NAME = "RemoteLogForwarder";

  private @Nullable EndermuxServer endermuxServer;
  private @Nullable EndermuxForwardingAppender forwardingAppender;

  public void start(final DedicatedServer server, final ConsoleState consoleState, final Config config) {
    final Path socketPath = server.getServerDirectory().toFile().toPath().resolve(config.consoleSocket().socketPath());

    this.forwardingAppender = new EndermuxForwardingAppender(
      LOG_FORWARDER_NAME,
      null,
      PatternLayout.newBuilder().withPattern(config.logPattern()).build()
    );

    this.endermuxServer = new EndermuxServer(
      this.forwardingAppender.logLayout(),
      socketPath,
      config.consoleSocket().maxConnections()
    );

    this.endermuxServer.start();
    EndermuxForwardingAppender.TARGET = new RemoteLogForwarder(this.endermuxServer);

    final Logger rootLogger = (Logger) LogManager.getRootLogger();
    final LoggerContext loggerContext = (LoggerContext) LogManager.getContext(false);
    final LoggerConfig loggerConfig = loggerContext.getConfiguration().getLoggerConfig(rootLogger.getName());

    this.forwardingAppender.start();
    loggerConfig.addAppender(this.forwardingAppender, loggerConfig.getLevel(), null);
    loggerContext.updateLoggers();

    this.endermuxServer.enableInteractivity(
      InteractiveConsoleHooks.builder()
        .completer(new FabricCommandCompleter(consoleState))
        .parser(new FabricCommandParser(consoleState))
        .executor(new FabricCommandExecutor(server))
        .highlighter(new FabricCommandHighlighter(server, config.highlightColors()))
        .build()
    );
  }

  public void close() {
    if (this.endermuxServer != null) {
      this.endermuxServer.stop();
      this.endermuxServer = null;
    }

    if (this.forwardingAppender != null) {
      final Logger rootLogger = (Logger) LogManager.getRootLogger();
      final LoggerContext loggerContext = (LoggerContext) LogManager.getContext(false);
      final LoggerConfig loggerConfig = loggerContext.getConfiguration().getLoggerConfig(rootLogger.getName());

      loggerConfig.removeAppender(LOG_FORWARDER_NAME);
      loggerContext.updateLoggers();
      this.forwardingAppender.stop();
    }

    EndermuxForwardingAppender.TARGET = null;
  }

  public void disableInteractivity() {
    if (this.endermuxServer != null) {
      this.endermuxServer.disableInteractivity();
    }
  }
}
