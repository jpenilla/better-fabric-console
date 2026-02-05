package xyz.jpenilla.endermux.client.runtime;

import java.io.Serial;
import java.util.Collections;
import java.util.Map;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.impl.ThrowableProxy;
import org.apache.logging.log4j.core.time.Instant;
import org.apache.logging.log4j.core.time.MutableInstant;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.message.SimpleMessage;
import org.apache.logging.log4j.util.ReadOnlyStringMap;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
final class SimpleLogEvent implements LogEvent {
  @Serial
  private static final long serialVersionUID = 4690946296839111645L;

  private final String loggerName;
  private final Level level;
  private final String message;
  private final long timestamp;
  private final String threadName;
  private final @Nullable Throwable thrown;
  private final long threadId;

  SimpleLogEvent(
    final String loggerName,
    final Level level,
    final String message,
    final long timestamp,
    final String threadName,
    final @Nullable Throwable thrown
  ) {
    this.loggerName = loggerName;
    this.level = level;
    this.message = message;
    this.timestamp = timestamp;
    this.threadName = threadName;
    this.thrown = thrown;
    this.threadId = Thread.currentThread().getId();
  }

  @Override
  public String getLoggerName() {
    return this.loggerName;
  }

  @Override
  public String getLoggerFqcn() {
    return this.loggerName;
  }

  @Override
  public Level getLevel() {
    return this.level;
  }

  @Override
  public Message getMessage() {
    return new SimpleMessage(this.message);
  }

  @Override
  public long getTimeMillis() {
    return this.timestamp;
  }

  @Override
  public Instant getInstant() {
    final MutableInstant instant = new MutableInstant();
    instant.initFromEpochMilli(this.timestamp, 0);
    return instant;
  }

  @Override
  public String getThreadName() {
    return this.threadName;
  }

  @Override
  public long getThreadId() {
    return this.threadId;
  }

  @Override
  public int getThreadPriority() {
    return Thread.NORM_PRIORITY;
  }

  @Override
  public @Nullable Throwable getThrown() {
    return this.thrown;
  }

  @Override
  public @Nullable ThrowableProxy getThrownProxy() {
    return this.thrown == null ? null : new ThrowableProxy(this.thrown);
  }

  @Override
  public boolean isIncludeLocation() {
    return false;
  }

  @Override
  public void setIncludeLocation(final boolean includeLocation) {
  }

  @Override
  public @Nullable StackTraceElement getSource() {
    return null;
  }

  @Override
  public @Nullable Marker getMarker() {
    return null;
  }

  @Override
  public @Nullable ReadOnlyStringMap getContextData() {
    return null;
  }

  @Override
  public Map<String, String> getContextMap() {
    return Collections.emptyMap();
  }

  @Override
  public ThreadContext.ContextStack getContextStack() {
    return ThreadContext.EMPTY_STACK;
  }

  @Override
  public long getNanoTime() {
    return 0;
  }

  @Override
  public boolean isEndOfBatch() {
    return false;
  }

  @Override
  public void setEndOfBatch(final boolean endOfBatch) {
  }

  @Override
  public LogEvent toImmutable() {
    return this;
  }
}
