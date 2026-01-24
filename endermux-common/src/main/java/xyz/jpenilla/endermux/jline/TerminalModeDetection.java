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
package xyz.jpenilla.endermux.jline;

import java.io.IOException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class TerminalModeDetection {
  private static final boolean CONSOLE_INPUT_AVAILABLE = System.console() != null;
  private static final TerminalMode MODE = detectMode();

  private TerminalModeDetection() {
  }

  private static TerminalMode detectMode() {
    if (!CONSOLE_INPUT_AVAILABLE) {
      return TerminalMode.DUMB;
    }

    try (Terminal terminal = TerminalBuilder.builder().system(true).dumb(false).build()) {
      return Terminal.TYPE_DUMB.equals(terminal.getType()) ? TerminalMode.DUMB : TerminalMode.INTERACTIVE;
    } catch (final IOException | IllegalStateException e) {
      return TerminalMode.DUMB;
    }
  }

  public static TerminalMode mode() {
    return MODE;
  }

  public static boolean isDumb() {
    return MODE == TerminalMode.DUMB;
  }

  public static boolean isInteractive() {
    return MODE == TerminalMode.INTERACTIVE;
  }

  public static boolean hasConsoleInput() {
    return CONSOLE_INPUT_AVAILABLE;
  }
}
