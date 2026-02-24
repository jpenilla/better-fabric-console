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

import java.util.ArrayList;
import java.util.List;
import org.jline.reader.Candidate;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;
import org.jspecify.annotations.NullMarked;
import xyz.jpenilla.betterfabricconsole.console.ConsoleState;
import xyz.jpenilla.endermux.protocol.Payloads;
import xyz.jpenilla.endermux.server.api.InteractiveConsoleHooks;

@NullMarked
public final class FabricCommandCompleter implements InteractiveConsoleHooks.CommandCompleter {
  private final ConsoleState consoleState;

  public FabricCommandCompleter(final ConsoleState consoleState) {
    this.consoleState = consoleState;
  }

  @Override
  public Payloads.CompletionResponse complete(final String command, final int cursor) {
    final LineReader dummyReader = this.consoleState.lineReader();
    final ParsedLine parsedLine = dummyReader.getParser().parse(command, cursor);

    final List<Candidate> candidates = new ArrayList<>();
    this.consoleState.completer().complete(dummyReader, parsedLine, candidates);

    final List<Payloads.CompletionResponse.CandidateInfo> candidateInfos = new ArrayList<>();
    for (final Candidate candidate : candidates) {
      candidateInfos.add(new Payloads.CompletionResponse.CandidateInfo(
        candidate.value(),
        candidate.displ(),
        candidate.descr()
      ));
    }

    return new Payloads.CompletionResponse(candidateInfos);
  }
}
