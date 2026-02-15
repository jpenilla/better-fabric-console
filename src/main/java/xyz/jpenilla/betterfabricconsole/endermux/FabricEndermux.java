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
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.dedicated.DedicatedServer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import xyz.jpenilla.betterfabricconsole.configuration.Config;
import xyz.jpenilla.betterfabricconsole.console.ConsoleState;
import xyz.jpenilla.endermux.server.EndermuxServer;
import xyz.jpenilla.endermux.server.api.InteractiveConsoleHooks;
import xyz.jpenilla.endermux.server.log4j.EndermuxForwardingAppender;

@NullMarked
public final class FabricEndermux {
  private @Nullable EndermuxServer endermuxServer;

  public void start(final Config config) {
    final Path socketPath = FabricLoader.getInstance().getGameDir().resolve(config.endermux().socketPath());

    this.endermuxServer = new EndermuxServer(
      socketPath,
      config.endermux().maxConnections()
    );

    EndermuxForwardingAppender.attach(this.endermuxServer);
    this.endermuxServer.start();
  }

  public void enableInteractivity(final DedicatedServer server, final ConsoleState consoleState, final Config config) {
    if (this.endermuxServer == null) {
      throw new IllegalStateException("Endermux server not started");
    }
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

    EndermuxForwardingAppender.detach();
  }

  public void disableInteractivity() {
    if (this.endermuxServer != null) {
      this.endermuxServer.disableInteractivity();
    }
  }
}
