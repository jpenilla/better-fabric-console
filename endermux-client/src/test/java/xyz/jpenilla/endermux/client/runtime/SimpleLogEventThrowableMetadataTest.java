package xyz.jpenilla.endermux.client.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.impl.ExtendedStackTraceElement;
import org.apache.logging.log4j.core.impl.ThrowableProxy;
import org.junit.jupiter.api.Test;
import xyz.jpenilla.endermux.protocol.Payloads;

class SimpleLogEventThrowableMetadataTest {
  @Test
  void rewritesProxyUsingForwardedThrowableMetadata() {
    final Payloads.ThrowableInfo cause = throwableInfo(
      "java.lang.IllegalArgumentException",
      "cause",
      frame("cause.Type", "causeMethod", "Cause.java", 12, "cause-loader", "cause.mod", "2.0.0", true, "cause-location", "2.0.0"),
      null,
      List.of()
    );
    final Payloads.ThrowableInfo suppressed = throwableInfo(
      "java.lang.RuntimeException",
      "suppressed",
      frame("supp.Type", "suppMethod", "Supp.java", 13, "supp-loader", "supp.mod", "3.0.0", false, "supp-location", "3.0.0"),
      null,
      List.of()
    );
    final Payloads.ThrowableInfo root = throwableInfo(
      "java.lang.IllegalStateException",
      "root",
      frame("root.Type", "rootMethod", "Root.java", 11, "root-loader", "root.mod", "1.0.0", true, "root-location", "1.0.0"),
      cause,
      List.of(suppressed)
    );

    final Throwable throwable = ThrowableInfoUtil.toThrowable(root);
    assertNotNull(throwable);
    assertEquals("root-loader", throwable.getStackTrace()[0].getClassLoaderName());
    assertEquals("root.mod", throwable.getStackTrace()[0].getModuleName());
    assertEquals("1.0.0", throwable.getStackTrace()[0].getModuleVersion());

    final SimpleLogEvent event = new SimpleLogEvent(
      "test.logger",
      Level.ERROR,
      "boom",
      123L,
      "Server thread",
      throwable,
      root
    );

    final ThrowableProxy proxy = event.getThrownProxy();
    assertNotNull(proxy);
    assertClassInfo(proxy.getExtendedStackTrace(), "root-location", "1.0.0");
    assertNotNull(proxy.getCauseProxy());
    assertClassInfo(proxy.getCauseProxy().getExtendedStackTrace(), "cause-location", "2.0.0");
    assertNotNull(proxy.getSuppressedProxies());
    assertEquals(1, proxy.getSuppressedProxies().length);
    assertClassInfo(proxy.getSuppressedProxies()[0].getExtendedStackTrace(), "supp-location", "3.0.0");
  }

  private static void assertClassInfo(
    final ExtendedStackTraceElement[] stackTrace,
    final String expectedLocation,
    final String expectedVersion
  ) {
    assertNotNull(stackTrace);
    assertEquals(1, stackTrace.length);
    assertEquals(expectedLocation, stackTrace[0].getExtraClassInfo().getLocation());
    assertEquals(expectedVersion, stackTrace[0].getExtraClassInfo().getVersion());
  }

  private static Payloads.ThrowableInfo throwableInfo(
    final String type,
    final String message,
    final Payloads.StackFrame frame,
    final Payloads.ThrowableInfo cause,
    final List<Payloads.ThrowableInfo> suppressed
  ) {
    return new Payloads.ThrowableInfo(type, message, List.of(frame), cause, suppressed);
  }

  private static Payloads.StackFrame frame(
    final String className,
    final String methodName,
    final String fileName,
    final int lineNumber,
    final String classLoaderName,
    final String moduleName,
    final String moduleVersion,
    final boolean exact,
    final String location,
    final String version
  ) {
    return new Payloads.StackFrame(
      className,
      methodName,
      fileName,
      lineNumber,
      classLoaderName,
      moduleName,
      moduleVersion,
      new Payloads.StackFrameClassInfo(exact, location, version)
    );
  }
}
