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

import java.util.regex.Pattern;
import org.jline.reader.Highlighter;
import org.jline.reader.LineReader;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class DelegatingHighlighter implements Highlighter {
  private @Nullable Highlighter delegate;

  @Override
  public AttributedString highlight(final LineReader reader, final String buffer) {
    if (this.delegate != null) {
      return this.delegate.highlight(reader, buffer);
    }
    final AttributedStringBuilder builder = new AttributedStringBuilder();
    builder.append(buffer, AttributedStyle.DEFAULT.foreground(AttributedStyle.RED));
    return builder.toAttributedString();
  }

  @Override
  public void setErrorPattern(final Pattern errorPattern) {
    if (this.delegate != null) {
      this.delegate.setErrorPattern(errorPattern);
    }
  }

  @Override
  public void setErrorIndex(final int errorIndex) {
    if (this.delegate != null) {
      this.delegate.setErrorIndex(errorIndex);
    }
  }

  public void delegateTo(final Highlighter highlighter) {
    this.delegate = highlighter;
  }
}
