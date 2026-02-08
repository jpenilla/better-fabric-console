package xyz.jpenilla.endermux.client.runtime;

import java.lang.reflect.Constructor;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import xyz.jpenilla.endermux.protocol.Payloads;

@NullMarked
final class ThrowableInfoUtil {
  private ThrowableInfoUtil() {
  }

  static @Nullable Throwable toThrowable(final Payloads.@Nullable ThrowableInfo info) {
    if (info == null) {
      return null;
    }
    final Throwable cause = toThrowable(info.cause());
    final Throwable[] suppressed = toSuppressed(info.suppressed());
    final Throwable throwable = instantiate(info, cause);
    throwable.setStackTrace(toStackTrace(info.frames()));
    if (cause != null && throwable.getCause() == null) {
      try {
        throwable.initCause(cause);
      } catch (final IllegalStateException ignored) {
        // already has cause
      }
    }
    for (final Throwable suppressedThrowable : suppressed) {
      if (suppressedThrowable != null) {
        throwable.addSuppressed(suppressedThrowable);
      }
    }
    return throwable;
  }

  private static Throwable instantiate(final Payloads.ThrowableInfo info, final @Nullable Throwable cause) {
    final String type = info.type();
    final String message = info.message();
    final Throwable reflected = tryInstantiate(type, message, cause);
    if (reflected != null) {
      return reflected;
    }
    return new RemoteThrowable(type, message);
  }

  private static @Nullable Throwable tryInstantiate(
    final String type,
    final @Nullable String message,
    final @Nullable Throwable cause
  ) {
    final Class<?> rawClass;
    try {
      rawClass = Class.forName(type, false, ThrowableInfoUtil.class.getClassLoader());
    } catch (final ClassNotFoundException ignored) {
      return null;
    }
    if (!Throwable.class.isAssignableFrom(rawClass)) {
      return null;
    }
    @SuppressWarnings("unchecked")
    final Class<? extends Throwable> throwableClass = (Class<? extends Throwable>) rawClass;
    final Throwable withCause = tryConstruct(throwableClass, message, cause);
    if (withCause != null) {
      return withCause;
    }
    final Throwable withMessage = tryConstruct(throwableClass, message);
    if (withMessage != null) {
      return withMessage;
    }
    final Throwable noArg = tryConstruct(throwableClass);
    return noArg;
  }

  private static @Nullable Throwable tryConstruct(
    final Class<? extends Throwable> type,
    final @Nullable String message,
    final @Nullable Throwable cause
  ) {
    try {
      final Constructor<? extends Throwable> constructor = type.getDeclaredConstructor(String.class, Throwable.class);
      constructor.setAccessible(true);
      return constructor.newInstance(message, cause);
    } catch (final ReflectiveOperationException ignored) {
      return null;
    }
  }

  private static @Nullable Throwable tryConstruct(
    final Class<? extends Throwable> type,
    final @Nullable String message
  ) {
    try {
      final Constructor<? extends Throwable> constructor = type.getDeclaredConstructor(String.class);
      constructor.setAccessible(true);
      return constructor.newInstance(message);
    } catch (final ReflectiveOperationException ignored) {
      return null;
    }
  }

  private static @Nullable Throwable tryConstruct(final Class<? extends Throwable> type) {
    try {
      final Constructor<? extends Throwable> constructor = type.getDeclaredConstructor();
      constructor.setAccessible(true);
      return constructor.newInstance();
    } catch (final ReflectiveOperationException ignored) {
      return null;
    }
  }

  private static StackTraceElement[] toStackTrace(final List<Payloads.StackFrame> frames) {
    final StackTraceElement[] elements = new StackTraceElement[frames.size()];
    for (int i = 0; i < frames.size(); i++) {
      final Payloads.StackFrame frame = frames.get(i);
      elements[i] = new StackTraceElement(
        frame.classLoaderName(),
        frame.moduleName(),
        frame.moduleVersion(),
        frame.className(),
        frame.methodName(),
        frame.fileName(),
        frame.lineNumber()
      );
    }
    return elements;
  }

  private static Throwable[] toSuppressed(final List<Payloads.ThrowableInfo> suppressed) {
    if (suppressed.isEmpty()) {
      return new Throwable[0];
    }
    final Throwable[] throwables = new Throwable[suppressed.size()];
    for (int i = 0; i < suppressed.size(); i++) {
      throwables[i] = toThrowable(suppressed.get(i));
    }
    return throwables;
  }

  private static final class RemoteThrowable extends Throwable {
    @java.io.Serial
    private static final long serialVersionUID = 1L;

    private final String originalType;

    private RemoteThrowable(final String originalType, final @Nullable String message) {
      super(message);
      this.originalType = originalType;
    }

    @Override
    public String getLocalizedMessage() {
      final String message = super.getLocalizedMessage();
      return message == null ? this.originalType : this.originalType + ": " + message;
    }
  }
}
