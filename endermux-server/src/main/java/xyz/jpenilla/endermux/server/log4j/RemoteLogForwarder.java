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
package xyz.jpenilla.endermux.server.log4j;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.impl.ExtendedClassInfo;
import org.apache.logging.log4j.core.impl.ExtendedStackTraceElement;
import org.apache.logging.log4j.core.impl.ThrowableProxy;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import xyz.jpenilla.endermux.protocol.Payloads;
import xyz.jpenilla.endermux.server.EndermuxServer;

@NullMarked
public final class RemoteLogForwarder implements EndermuxForwardingAppender.LogForwardingTarget {

  public static final String COMPONENT_LOG_MESSAGE_KEY = "component_log_message";

  private static final Logger LOGGER = LogManager.getLogger();

  private static final long ERROR_LOG_INTERVAL_MS = 60_000;

  private final EndermuxServer endermuxServer;
  private final AtomicLong failureCount = new AtomicLong(0);
  private volatile long lastErrorLogTime = 0;

  public RemoteLogForwarder(final EndermuxServer endermuxServer) {
    this.endermuxServer = endermuxServer;
  }

  @Override
  public void forward(final LogEvent event) {
    final EndermuxServer manager = this.endermuxServer;
    if (!manager.isRunning()) {
      return;
    }

    try {
      final String rawMessage = event.getMessage().getFormattedMessage();

      final Payloads.LogForward logPayload = new Payloads.LogForward(
        event.getLoggerName(),
        event.getLevel().toString(),
        rawMessage,
        event.getContextData().getValue(COMPONENT_LOG_MESSAGE_KEY),
        throwableInfo(event),
        event.getTimeMillis(),
        event.getThreadName()
      );

      manager.broadcastLog(logPayload);

    } catch (final Exception e) {
      this.handleForwardingError(e);
    }
  }

  private void handleForwardingError(final Exception e) {
    final long count = this.failureCount.incrementAndGet();
    final long now = System.currentTimeMillis();

    if (now - this.lastErrorLogTime > ERROR_LOG_INTERVAL_MS) {
      this.lastErrorLogTime = now;
      LOGGER.debug(
        "Failed to forward log event to socket clients (total failures: {}): {}",
        count,
        e.getMessage(),
        e
      );
    }
  }

  private static Payloads.@Nullable ThrowableInfo throwableInfo(final LogEvent event) {
    final Throwable thrown = event.getThrown();
    if (thrown != null) {
      return toThrowableInfo(thrown);
    }
    final ThrowableProxy proxy = event.getThrownProxy();
    return proxy == null ? null : toThrowableInfo(proxy);
  }

  private static Payloads.ThrowableInfo toThrowableInfo(final Throwable throwable) {
    final StackTraceElement[] stackTrace = throwable.getStackTrace();
    final Payloads.StackFrame[] frames = new Payloads.StackFrame[stackTrace.length];
    for (int i = 0; i < stackTrace.length; i++) {
      final StackTraceElement element = stackTrace[i];
      frames[i] = new Payloads.StackFrame(
        element.getClassName(),
        element.getMethodName(),
        element.getFileName(),
        element.getLineNumber(),
        element.getClassLoaderName(),
        element.getModuleName(),
        element.getModuleVersion(),
        null
      );
    }
    final Throwable[] suppressed = throwable.getSuppressed();
    final Payloads.ThrowableInfo[] suppressedInfo = new Payloads.ThrowableInfo[suppressed.length];
    for (int i = 0; i < suppressed.length; i++) {
      suppressedInfo[i] = toThrowableInfo(suppressed[i]);
    }
    return new Payloads.ThrowableInfo(
      throwable.getClass().getName(),
      throwable.getMessage(),
      Arrays.asList(frames),
      throwable.getCause() == null ? null : toThrowableInfo(throwable.getCause()),
      Arrays.asList(suppressedInfo)
    );
  }

  private static Payloads.ThrowableInfo toThrowableInfo(final ThrowableProxy proxy) {
    final ExtendedStackTraceElement[] stackTrace = proxy.getExtendedStackTrace();
    final Payloads.StackFrame[] frames = new Payloads.StackFrame[stackTrace.length];
    for (int i = 0; i < stackTrace.length; i++) {
      final ExtendedStackTraceElement extendedElement = stackTrace[i];
      final StackTraceElement element = extendedElement.getStackTraceElement();
      final ExtendedClassInfo classInfo = extendedElement.getExtraClassInfo();
      frames[i] = new Payloads.StackFrame(
        element.getClassName(),
        element.getMethodName(),
        element.getFileName(),
        element.getLineNumber(),
        element.getClassLoaderName(),
        element.getModuleName(),
        element.getModuleVersion(),
        classInfo == null ? null : new Payloads.StackFrameClassInfo(
          classInfo.getExact(),
          classInfo.getLocation(),
          classInfo.getVersion()
        )
      );
    }
    final ThrowableProxy[] suppressed = proxy.getSuppressedProxies();
    final Payloads.ThrowableInfo[] suppressedInfo = suppressed == null
      ? new Payloads.ThrowableInfo[0]
      : new Payloads.ThrowableInfo[suppressed.length];
    if (suppressed != null) {
      for (int i = 0; i < suppressed.length; i++) {
        suppressedInfo[i] = toThrowableInfo(suppressed[i]);
      }
    }
    return new Payloads.ThrowableInfo(
      proxy.getName(),
      proxy.getMessage(),
      Arrays.asList(frames),
      proxy.getCauseProxy() == null ? null : toThrowableInfo(proxy.getCauseProxy()),
      Arrays.asList(suppressedInfo)
    );
  }
}
