package xyz.jpenilla.endermux.jline;

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
        if (entry.getValue().stream().allMatch(MinecraftCandidate.class::isInstance)) {
          candidates.put(entry.getKey(), entry.getValue());
        }
      }
      return candidates;
    });
  }
}
