package xyz.jpenilla.endermux.client.runtime;

import java.io.Serial;
import java.util.Collections;
import java.util.Map;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.impl.ExtendedClassInfo;
import org.apache.logging.log4j.core.impl.ExtendedStackTraceElement;
import org.apache.logging.log4j.core.impl.ThrowableProxy;
import org.apache.logging.log4j.core.time.Instant;
import org.apache.logging.log4j.core.time.MutableInstant;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.message.SimpleMessage;
import org.apache.logging.log4j.util.ReadOnlyStringMap;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import xyz.jpenilla.endermux.protocol.Payloads;

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
  private final transient Payloads.@Nullable ThrowableInfo throwableInfo;
  private final long threadId;
  private transient @Nullable ThrowableProxy thrownProxy;

  SimpleLogEvent(
    final String loggerName,
    final Level level,
    final String message,
    final long timestamp,
    final String threadName,
    final @Nullable Throwable thrown,
    final Payloads.@Nullable ThrowableInfo throwableInfo
  ) {
    this.loggerName = loggerName;
    this.level = level;
    this.message = message;
    this.timestamp = timestamp;
    this.threadName = threadName;
    this.thrown = thrown;
    this.throwableInfo = throwableInfo;
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
    if (this.thrown == null) {
      return null;
    }
    ThrowableProxy proxy = this.thrownProxy;
    if (proxy != null) {
      return proxy;
    }
    proxy = new ThrowableProxy(this.thrown);
    if (this.throwableInfo != null) {
      rewriteStackTrace(proxy, this.throwableInfo);
    }
    this.thrownProxy = proxy;
    return proxy;
  }

  private static void rewriteStackTrace(final ThrowableProxy proxy, final Payloads.ThrowableInfo info) {
    final ExtendedStackTraceElement[] stackTrace = proxy.getExtendedStackTrace();
    final int frameCount = Math.min(stackTrace.length, info.frames().size());
    for (int i = 0; i < frameCount; i++) {
      final ExtendedStackTraceElement extendedElement = stackTrace[i];
      final Payloads.StackFrame frame = info.frames().get(i);
      final Payloads.StackFrameClassInfo incomingClassInfo = frame.classInfo();
      final ExtendedClassInfo existingClassInfo = extendedElement.getExtraClassInfo();

      final ExtendedClassInfo rewrittenClassInfo;
      if (incomingClassInfo != null) {
        rewrittenClassInfo = new ExtendedClassInfo(
          incomingClassInfo.exact(),
          incomingClassInfo.location(),
          incomingClassInfo.version()
        );
      } else if (existingClassInfo.getLocation().equals("?") && frame.classLoaderName() != null) {
        rewrittenClassInfo = new ExtendedClassInfo(
          existingClassInfo.getExact(),
          frame.classLoaderName(),
          existingClassInfo.getVersion()
        );
      } else {
        continue;
      }

      stackTrace[i] = new ExtendedStackTraceElement(extendedElement.getStackTraceElement(), rewrittenClassInfo);
    }

    final ThrowableProxy causeProxy = proxy.getCauseProxy();
    final Payloads.ThrowableInfo causeInfo = info.cause();
    if (causeProxy != null && causeInfo != null) {
      rewriteStackTrace(causeProxy, causeInfo);
    }

    final ThrowableProxy[] suppressedProxies = proxy.getSuppressedProxies();
    if (suppressedProxies == null || suppressedProxies.length == 0 || info.suppressed().isEmpty()) {
      return;
    }
    final int suppressedCount = Math.min(suppressedProxies.length, info.suppressed().size());
    for (int i = 0; i < suppressedCount; i++) {
      rewriteStackTrace(suppressedProxies[i], info.suppressed().get(i));
    }
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
