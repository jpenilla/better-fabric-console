package xyz.jpenilla.endermux.client.parser;

import java.util.List;
import org.jline.reader.CompletingParsedLine;

public record RemoteParsedLine(
  String word,
  int wordCursor,
  int wordIndex,
  List<String> words,
  String line,
  int cursor
) implements CompletingParsedLine {

  @Override
  public CharSequence escape(final CharSequence candidate, final boolean complete) {
    return candidate;
  }

  @Override
  public int rawWordCursor() {
    return this.wordCursor;
  }

  @Override
  public int rawWordLength() {
    return this.word.length();
  }
}
