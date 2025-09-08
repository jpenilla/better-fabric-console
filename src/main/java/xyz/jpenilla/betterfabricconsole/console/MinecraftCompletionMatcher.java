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

import com.google.common.collect.Iterables;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jline.reader.Candidate;
import org.jline.reader.CompletingParsedLine;
import org.jline.reader.LineReader;
import org.jline.reader.impl.CompletionMatcherImpl;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class MinecraftCompletionMatcher extends CompletionMatcherImpl {

  @Override
  protected void defaultMatchers(final Map<LineReader.Option, Boolean> options, final boolean prefix, final CompletingParsedLine line, final boolean caseInsensitive, final int errors, final String originalGroupName) {
    super.defaultMatchers(options, prefix, line, caseInsensitive, errors, originalGroupName);
    this.matchers.addFirst(m -> {
      final Map<String, List<Candidate>> candidates = new HashMap<>();
      for (final Map.Entry<String, List<Candidate>> entry : m.entrySet()) {
        if (Iterables.all(entry.getValue(), MinecraftCommandCompleter.MinecraftCandidate.class::isInstance)) {
          candidates.put(entry.getKey(), entry.getValue());
        }
      }
      return candidates;
    });
  }
}
