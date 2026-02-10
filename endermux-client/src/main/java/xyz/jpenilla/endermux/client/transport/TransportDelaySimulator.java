package xyz.jpenilla.endermux.client.transport;

import java.io.IOException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import xyz.jpenilla.endermux.protocol.Message;

@NullMarked
final class TransportDelaySimulator {
  private static final String DELAY_BASE_PROPERTY = "endermux.client.testTransportDelayBaseMs";
  private static final String DELAY_JITTER_PROPERTY = "endermux.client.testTransportDelayJitterMs";

  private final long baseDelayMs;
  private final long jitterMs;
  private final AtomicBoolean running = new AtomicBoolean(false);
  private final AtomicLong outboundDelayFloorNanos = new AtomicLong(0L);
  private final AtomicLong inboundDelayFloorNanos = new AtomicLong(0L);
  private final LinkedBlockingQueue<DelayedMessage> outboundQueue = new LinkedBlockingQueue<>();
  private final LinkedBlockingQueue<DelayedMessage> inboundQueue = new LinkedBlockingQueue<>();

  private volatile @Nullable ThrowingMessageConsumer outboundHandler;
  private volatile @Nullable Consumer<Message<?>> inboundHandler;
  private volatile @Nullable BooleanSupplier connectionOpen;
  private volatile @Nullable Runnable outboundIoFailureHandler;
  private @Nullable Thread outboundWorker;
  private @Nullable Thread inboundWorker;

  private TransportDelaySimulator(final long baseDelayMs, final long jitterMs) {
    this.baseDelayMs = baseDelayMs;
    this.jitterMs = jitterMs;
  }

  static TransportDelaySimulator fromSystemProperties(final Logger logger) {
    final long base = Math.max(0L, Long.getLong(DELAY_BASE_PROPERTY, 0L));
    final long jitter = Math.max(0L, Long.getLong(DELAY_JITTER_PROPERTY, 0L));
    if (base > 0L || jitter > 0L) {
      logger.info("Client transport test delay enabled (base={}ms, jitter={}ms)", base, jitter);
    }
    return new TransportDelaySimulator(base, jitter);
  }

  boolean enabled() {
    return this.baseDelayMs > 0L || this.jitterMs > 0L;
  }

  void start(
    final ThrowingMessageConsumer outboundHandler,
    final Consumer<Message<?>> inboundHandler,
    final BooleanSupplier connectionOpen,
    final Runnable outboundIoFailureHandler
  ) {
    if (!this.enabled() || !this.running.compareAndSet(false, true)) {
      return;
    }
    this.outboundHandler = outboundHandler;
    this.inboundHandler = inboundHandler;
    this.connectionOpen = connectionOpen;
    this.outboundIoFailureHandler = outboundIoFailureHandler;

    this.outboundWorker = Thread.ofVirtual().name("SocketTransport-DelayedWriter").start(this::runOutboundWorker);
    this.inboundWorker = Thread.ofVirtual().name("SocketTransport-DelayedInbound").start(this::runInboundWorker);
  }

  void stop() {
    if (!this.running.compareAndSet(true, false)) {
      return;
    }
    final Thread outbound = this.outboundWorker;
    final Thread inbound = this.inboundWorker;
    if (outbound != null) {
      outbound.interrupt();
      this.outboundWorker = null;
    }
    if (inbound != null) {
      inbound.interrupt();
      this.inboundWorker = null;
    }
    this.outboundQueue.clear();
    this.inboundQueue.clear();
    this.outboundDelayFloorNanos.set(0L);
    this.inboundDelayFloorNanos.set(0L);
  }

  void enqueueOutbound(final Message<?> message) {
    if (!this.running.get()) {
      return;
    }
    this.outboundQueue.offer(new DelayedMessage(message, this.computeDelayedDueNanos(this.outboundDelayFloorNanos)));
  }

  void enqueueInbound(final Message<?> message) {
    if (!this.running.get()) {
      return;
    }
    this.inboundQueue.offer(new DelayedMessage(message, this.computeDelayedDueNanos(this.inboundDelayFloorNanos)));
  }

  private void runOutboundWorker() {
    while (this.running.get()) {
      try {
        final DelayedMessage queued = this.outboundQueue.take();
        this.awaitDueTime(queued.dueNanos());

        final ThrowingMessageConsumer handler = this.outboundHandler;
        if (handler == null) {
          continue;
        }
        handler.accept(queued.message());
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      } catch (final IOException e) {
        final Runnable failureHandler = this.outboundIoFailureHandler;
        if (failureHandler != null) {
          failureHandler.run();
        }
        break;
      }
    }
  }

  private void runInboundWorker() {
    while (this.running.get()) {
      try {
        final DelayedMessage queued = this.inboundQueue.take();
        this.awaitDueTime(queued.dueNanos());

        final BooleanSupplier openCheck = this.connectionOpen;
        if (openCheck != null && !openCheck.getAsBoolean()) {
          break;
        }

        final Consumer<Message<?>> handler = this.inboundHandler;
        if (handler != null) {
          handler.accept(queued.message());
        }
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      }
    }
  }

  private void awaitDueTime(final long dueNanos) {
    while (true) {
      final long waitNanos = dueNanos - System.nanoTime();
      if (waitNanos <= 0L) {
        return;
      }
      LockSupport.parkNanos(waitNanos);
      if (Thread.currentThread().isInterrupted()) {
        return;
      }
    }
  }

  private long computeDelayedDueNanos(final AtomicLong floorNanos) {
    final long delayNanos = TimeUnit.MILLISECONDS.toNanos(this.sampleDelayMs());
    final long target = System.nanoTime() + delayNanos;
    while (true) {
      final long previous = floorNanos.get();
      final long due = Math.max(previous, target);
      if (floorNanos.compareAndSet(previous, due)) {
        return due;
      }
    }
  }

  private long sampleDelayMs() {
    long delayMs = this.baseDelayMs;
    if (this.jitterMs > 0L) {
      delayMs += ThreadLocalRandom.current().nextLong(-this.jitterMs, this.jitterMs + 1L);
    }
    if (delayMs <= 0L) {
      return 0L;
    }
    return delayMs;
  }

  private record DelayedMessage(Message<?> message, long dueNanos) {
  }

  @FunctionalInterface
  interface ThrowingMessageConsumer {
    void accept(Message<?> message) throws IOException;
  }
}
