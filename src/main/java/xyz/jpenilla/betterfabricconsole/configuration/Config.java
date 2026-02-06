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
package xyz.jpenilla.betterfabricconsole.configuration;

import org.jspecify.annotations.NullMarked;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Comment;

@ConfigSerializable
@NullMarked
public final class Config {
  /**
   * Mirrors {@link org.jline.utils.AttributedStyle} color constants.
   */
  public enum StyleColor {
    BLACK(0),
    RED(1),
    GREEN(2),
    YELLOW(3),
    BLUE(4),
    MAGENTA(5),
    CYAN(6),
    WHITE(7);

    private final int index;

    StyleColor(final int index) {
      this.index = index;
    }

    public int index() {
      return this.index;
    }
  }

  public static final String DEFAULT_LOG_PATTERN =
    "%highlight{[%d{HH:mm:ss} %level] [%t]: [%logger{1}]}{FATAL=red, ERROR=red, WARN=yellow, INFO=default, DEBUG=yellow, TRACE=blue} %legacyMinecraftFormatting{%msg}%n";

  @Comment("Log4j logger pattern. See https://logging.apache.org/log4j/2.x/manual/layouts.html#Patterns for documentation.")
  private String logPattern = DEFAULT_LOG_PATTERN;

  public String logPattern() {
    return this.logPattern;
  }

  @Comment("Specify argument highlight colors, in order. Possible values: [BLACK, RED, GREEN, YELLOW, BLUE, MAGENTA, CYAN, WHITE]")
  private StyleColor[] highlightColors = {StyleColor.CYAN, StyleColor.YELLOW, StyleColor.GREEN, StyleColor.MAGENTA, /*GOLD on client*/StyleColor.BLUE};

  public StyleColor[] highlightColors() {
    return this.highlightColors;
  }

  @Comment("Whether to log commands executed by players to console.")
  private boolean logPlayerExecutedCommands = true;

  public boolean logPlayerExecutedCommands() {
    return this.logPlayerExecutedCommands;
  }

  @Comment("Console socket server configuration.")
  private ConsoleSocketConfig consoleSocket = new ConsoleSocketConfig();

  public ConsoleSocketConfig consoleSocket() {
    return this.consoleSocket;
  }

  @ConfigSerializable
  public static final class ConsoleSocketConfig {
    @Comment("Whether to enable Unix socket console connections.")
    private boolean enabled = false;

    public boolean enabled() {
      return this.enabled;
    }

    @Comment("Path to the Unix socket file relative to the server directory.")
    private String socketPath = "console.sock";

    public String socketPath() {
      return this.socketPath;
    }

    @Comment("Maximum number of concurrent socket connections.")
    private int maxConnections = 5;

    public int maxConnections() {
      return this.maxConnections;
    }
  }
}
