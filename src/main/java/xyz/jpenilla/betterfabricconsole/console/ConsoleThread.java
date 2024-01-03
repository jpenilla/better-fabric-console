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

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.framework.qual.DefaultQualifier;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.UserInterruptException;
import xyz.jpenilla.betterfabricconsole.BetterFabricConsole;

@DefaultQualifier(NonNull.class)
public final class ConsoleThread extends Thread {
  private static final String TERMINAL_PROMPT = "> ";
  private static final String STOP_COMMAND = "stop";

  private final DedicatedServer server;
  private final LineReader lineReader;

  public ConsoleThread(
    final DedicatedServer server,
    final LineReader lineReader
  ) {
    super("Console thread");
    this.server = server;
    this.lineReader = lineReader;
  }

  @Override
  public void run() {
    BetterFabricConsole.LOGGER.info("Initialized Better Fabric Console console thread.");
    this.acceptInput();
  }

  private static boolean isRunning(final MinecraftServer server) {
    return !server.isStopped() && server.isRunning();
  }

  private void acceptInput() {
    while (isRunning(this.server)) {
      try {
        final String input = this.lineReader.readLine(TERMINAL_PROMPT).trim();
        if (input.isEmpty()) {
          continue;
        }
        this.server.handleConsoleInput(input, this.server.createCommandSourceStack());
        if (input.equals(STOP_COMMAND)) {
          break;
        }
      } catch (final EndOfFileException | UserInterruptException ex) {
        this.server.handleConsoleInput(STOP_COMMAND, this.server.createCommandSourceStack());
        break;
      }
    }
  }
}
