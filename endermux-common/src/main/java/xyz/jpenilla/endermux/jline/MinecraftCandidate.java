package xyz.jpenilla.endermux.jline;

import org.jline.reader.Candidate;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * A marker subclass of {@link Candidate} used to identify Minecraft command completions.
 * This is used by {@link MinecraftCompletionMatcher} to prioritize Minecraft-specific completions.
 */
@NullMarked
public final class MinecraftCandidate extends Candidate {
  public MinecraftCandidate(
    final String value,
    final String display,
    final @Nullable String group,
    final @Nullable String description,
    final @Nullable String suffix,
    final @Nullable String key,
    final boolean complete
  ) {
    super(value, display, group, description, suffix, key, complete);
  }
}
