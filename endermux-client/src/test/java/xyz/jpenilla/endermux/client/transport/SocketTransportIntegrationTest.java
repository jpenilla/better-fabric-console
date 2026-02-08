package xyz.jpenilla.endermux.client.transport;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import xyz.jpenilla.endermux.protocol.FrameCodec;
import xyz.jpenilla.endermux.protocol.LayoutConfig;
import xyz.jpenilla.endermux.protocol.Message;
import xyz.jpenilla.endermux.protocol.MessageSerializer;
import xyz.jpenilla.endermux.protocol.MessageType;
import xyz.jpenilla.endermux.protocol.Payloads;
import xyz.jpenilla.endermux.protocol.SocketProtocolConstants;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SocketTransportIntegrationTest {
  @TempDir
  Path tempDir;

  @Test
  void handshakeRejectWithDifferentExpectedVersionThrowsProtocolMismatch() throws Exception {
    try (ScriptedServer server = this.startServer(peer -> {
      final Message<?> hello = peer.readMessage();
      final String requestId = assertHello(hello);
      peer.write(Message.response(
        requestId,
        MessageType.REJECT,
        new Payloads.Reject("Unsupported protocol version", SocketProtocolConstants.PROTOCOL_VERSION + 1)
      ));
    })) {
      final SocketTransport transport = new SocketTransport(server.socketPath().toString());

      final ProtocolMismatchException ex = assertThrows(ProtocolMismatchException.class, transport::connect);
      assertEquals(SocketProtocolConstants.PROTOCOL_VERSION + 1, ex.expectedVersion());
      assertEquals(SocketProtocolConstants.PROTOCOL_VERSION, ex.actualVersion());
      transport.disconnect();
    }
  }

  @Test
  void handshakeInvalidResponseTypeThrowsIOException() throws Exception {
    try (ScriptedServer server = this.startServer(peer -> {
      final Message<?> hello = peer.readMessage();
      final String requestId = assertHello(hello);
      peer.write(Message.response(requestId, MessageType.PONG, new Payloads.Pong()));
    })) {
      final SocketTransport transport = new SocketTransport(server.socketPath().toString());

      final IOException ex = assertThrows(IOException.class, transport::connect);
      assertTrue(ex.getMessage().contains("Invalid handshake response"));
      transport.disconnect();
    }
  }

  @Test
  void handshakeWelcomeWrongVersionThrowsProtocolMismatch() throws Exception {
    try (ScriptedServer server = this.startServer(peer -> {
      final Message<?> hello = peer.readMessage();
      final String requestId = assertHello(hello);
      peer.write(Message.response(
        requestId,
        MessageType.WELCOME,
        new Payloads.Welcome(SocketProtocolConstants.PROTOCOL_VERSION + 1, layout())
      ));
    })) {
      final SocketTransport transport = new SocketTransport(server.socketPath().toString());

      final ProtocolMismatchException ex = assertThrows(ProtocolMismatchException.class, transport::connect);
      assertEquals(SocketProtocolConstants.PROTOCOL_VERSION + 1, ex.expectedVersion());
      assertEquals(SocketProtocolConstants.PROTOCOL_VERSION, ex.actualVersion());
      transport.disconnect();
    }
  }

  @Test
  void correlatedResponseCompletesPendingRequest() throws Exception {
    try (ScriptedServer server = this.startServer(peer -> {
      final Message<?> hello = peer.readMessage();
      final String helloRequestId = assertHello(hello);
      peer.write(Message.response(
        helloRequestId,
        MessageType.WELCOME,
        new Payloads.Welcome(SocketProtocolConstants.PROTOCOL_VERSION, layout())
      ));

      final Message<?> ping = peer.readMessage();
      assertEquals(MessageType.PING, ping.type());
      assertNotNull(ping.requestId());
      peer.write(Message.response(ping.requestId(), MessageType.PONG, new Payloads.Pong()));
    })) {
      final SocketTransport transport = new SocketTransport(server.socketPath().toString());
      transport.connect();
      try {
        final Message<Payloads.Ping> request = transport.createRequest(MessageType.PING, new Payloads.Ping());
        final Message<?> response = transport.sendMessageAndWaitForResponse(request, MessageType.PONG, 2_000L);
        assertEquals(MessageType.PONG, response.type());
        assertEquals(request.requestId(), response.requestId());
      } finally {
        transport.disconnect();
      }
    }
  }

  @Test
  void errorResponseSurfacesAsIOException() throws Exception {
    try (ScriptedServer server = this.startServer(peer -> {
      final Message<?> hello = peer.readMessage();
      final String helloRequestId = assertHello(hello);
      peer.write(Message.response(
        helloRequestId,
        MessageType.WELCOME,
        new Payloads.Welcome(SocketProtocolConstants.PROTOCOL_VERSION, layout())
      ));

      final Message<?> ping = peer.readMessage();
      peer.write(Message.response(ping.requestId(), MessageType.ERROR, new Payloads.Error("Nope", "Bad ping")));
    })) {
      final SocketTransport transport = new SocketTransport(server.socketPath().toString());
      transport.connect();
      try {
        final Message<Payloads.Ping> request = transport.createRequest(MessageType.PING, new Payloads.Ping());
        final IOException ex = assertThrows(
          IOException.class,
          () -> transport.sendMessageAndWaitForResponse(request, MessageType.PONG, 2_000L)
        );
        assertEquals("Nope: Bad ping", ex.getMessage());
      } finally {
        transport.disconnect();
      }
    }
  }

  @Test
  void wrongResponseTypeSurfacesAsIOException() throws Exception {
    try (ScriptedServer server = this.startServer(peer -> {
      final Message<?> hello = peer.readMessage();
      final String helloRequestId = assertHello(hello);
      peer.write(Message.response(
        helloRequestId,
        MessageType.WELCOME,
        new Payloads.Welcome(SocketProtocolConstants.PROTOCOL_VERSION, layout())
      ));

      final Message<?> ping = peer.readMessage();
      peer.write(Message.response(ping.requestId(), MessageType.PONG, new Payloads.Pong()));
    })) {
      final SocketTransport transport = new SocketTransport(server.socketPath().toString());
      transport.connect();
      try {
        final Message<Payloads.Ping> request = transport.createRequest(MessageType.PING, new Payloads.Ping());
        final IOException ex = assertThrows(
          IOException.class,
          () -> transport.sendMessageAndWaitForResponse(request, MessageType.COMPLETION_RESPONSE, 2_000L)
        );
        assertTrue(ex.getMessage().contains("Unexpected response type"));
      } finally {
        transport.disconnect();
      }
    }
  }

  @Test
  void interactivityUnavailableBlocksGatedRequestClientSide() throws Exception {
    try (ScriptedServer server = this.startServer(peer -> {
      final Message<?> hello = peer.readMessage();
      final String helloRequestId = assertHello(hello);
      peer.write(Message.response(
        helloRequestId,
        MessageType.WELCOME,
        new Payloads.Welcome(SocketProtocolConstants.PROTOCOL_VERSION, layout())
      ));
      Thread.sleep(100L);
    })) {
      final SocketTransport transport = new SocketTransport(server.socketPath().toString());
      transport.connect();
      try {
        assertFalse(transport.isInteractivityAvailable());
        final Message<Payloads.CompletionRequest> request = transport.createRequest(
          MessageType.COMPLETION_REQUEST,
          new Payloads.CompletionRequest("help", 4)
        );
        final IOException ex = assertThrows(
          IOException.class,
          () -> transport.sendMessageAndWaitForResponse(request, MessageType.COMPLETION_RESPONSE, 2_000L)
        );
        assertEquals("Interactivity is currently unavailable", ex.getMessage());
      } finally {
        transport.disconnect();
      }
    }
  }

  @Test
  void interactivityStatusMessageUpdatesTransportState() throws Exception {
    try (ScriptedServer server = this.startServer(peer -> {
      final Message<?> hello = peer.readMessage();
      final String helloRequestId = assertHello(hello);
      peer.write(Message.response(
        helloRequestId,
        MessageType.WELCOME,
        new Payloads.Welcome(SocketProtocolConstants.PROTOCOL_VERSION, layout())
      ));
      peer.write(Message.unsolicited(MessageType.INTERACTIVITY_STATUS, new Payloads.InteractivityStatus(true)));
      Thread.sleep(100L);
    })) {
      final SocketTransport transport = new SocketTransport(server.socketPath().toString());
      transport.connect();
      try {
        waitForCondition(Duration.ofSeconds(2), transport::isInteractivityAvailable);
        assertTrue(transport.isInteractivityAvailable());
      } finally {
        transport.disconnect();
      }
    }
  }

  private ScriptedServer startServer(final ServerScript script) throws Exception {
    final Path socket = this.tempDir.resolve("sock-" + UUID.randomUUID() + ".sock");
    final ScriptedServer server = new ScriptedServer(socket, script);
    final long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
    while (!Files.exists(socket)) {
      if (System.nanoTime() > deadline) {
        throw new AssertionError("Timed out waiting for socket file");
      }
      Thread.sleep(10L);
    }
    return server;
  }

  private static LayoutConfig layout() {
    return LayoutConfig.pattern("%msg%n", new LayoutConfig.Flags(true, false, false), "UTF-8");
  }

  private static String assertHello(final Message<?> hello) {
    assertNotNull(hello);
    assertEquals(MessageType.HELLO, hello.type());
    assertInstanceOf(Payloads.Hello.class, hello.payload());
    assertNotNull(hello.requestId());
    return hello.requestId();
  }

  private static void waitForCondition(final Duration timeout, final Condition condition) throws Exception {
    final long deadline = System.nanoTime() + timeout.toNanos();
    while (!condition.test()) {
      if (System.nanoTime() > deadline) {
        throw new AssertionError("Condition was not met before timeout");
      }
      Thread.sleep(10L);
    }
  }

  @FunctionalInterface
  private interface Condition {
    boolean test();
  }

  @FunctionalInterface
  private interface ServerScript {
    void run(TestPeer peer) throws Exception;
  }

  private static final class ScriptedServer implements AutoCloseable {
    private final ServerSocketChannel serverChannel;
    private final Path socketPath;
    private final Thread thread;
    private final AtomicReference<Throwable> failure = new AtomicReference<>();

    ScriptedServer(final Path socketPath, final ServerScript script) throws IOException {
      this.socketPath = socketPath;
      this.serverChannel = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
      this.serverChannel.bind(UnixDomainSocketAddress.of(socketPath));
      this.thread = Thread.ofVirtual().start(() -> {
        try (SocketChannel client = this.serverChannel.accept(); TestPeer peer = new TestPeer(client)) {
          script.run(peer);
        } catch (final Throwable t) {
          this.failure.set(t);
        }
      });
    }

    Path socketPath() {
      return this.socketPath;
    }

    @Override
    public void close() throws IOException {
      try {
        this.serverChannel.close();
      } finally {
        try {
          this.thread.join(2_000L);
        } catch (final InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new IOException("Interrupted while waiting for test server thread", e);
        }
      }
      final Throwable t = this.failure.get();
      if (t != null) {
        if (t instanceof IOException ex) {
          throw ex;
        }
        if (t instanceof RuntimeException ex) {
          throw ex;
        }
        throw new IOException("Test server failed", t);
      }
    }
  }

  private static final class TestPeer implements AutoCloseable {
    private final DataInputStream input;
    private final DataOutputStream output;
    private final MessageSerializer serializer = MessageSerializer.createStandard();

    TestPeer(final SocketChannel channel) throws IOException {
      this.input = new DataInputStream(new BufferedInputStream(Channels.newInputStream(channel)));
      this.output = new DataOutputStream(new BufferedOutputStream(Channels.newOutputStream(channel)));
    }

    Message<?> readMessage() throws IOException {
      final byte[] frame = FrameCodec.readFrame(this.input);
      if (frame == null) {
        return null;
      }
      return this.serializer.deserialize(new String(frame, StandardCharsets.UTF_8));
    }

    void write(final Message<?> message) throws IOException {
      final byte[] frame = this.serializer.serialize(message).getBytes(StandardCharsets.UTF_8);
      FrameCodec.writeFrame(this.output, frame);
    }

    @Override
    public void close() throws IOException {
      this.output.close();
      this.input.close();
    }
  }
}
