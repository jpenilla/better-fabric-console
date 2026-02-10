package xyz.jpenilla.endermux.server;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import xyz.jpenilla.endermux.protocol.FrameCodec;
import xyz.jpenilla.endermux.protocol.LayoutConfig;
import xyz.jpenilla.endermux.protocol.Message;
import xyz.jpenilla.endermux.protocol.MessageSerializer;
import xyz.jpenilla.endermux.protocol.MessageType;
import xyz.jpenilla.endermux.protocol.Payloads;
import xyz.jpenilla.endermux.protocol.SocketProtocolConstants;
import xyz.jpenilla.endermux.protocol.TimedRead;
import xyz.jpenilla.endermux.server.api.InteractiveConsoleHooks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EndermuxServerIntegrationTest {
  @TempDir
  Path tempDir;

  private EndermuxServer server;

  @AfterEach
  void tearDown() {
    if (this.server != null) {
      this.server.stop();
    }
  }

  @Test
  void handshakeSuccessSendsWelcomeAndInitialInteractivityStatus() throws Exception {
    final Path socket = this.startServer();

    try (TestClient client = TestClient.connect(socket)) {
      final String requestId = UUID.randomUUID().toString();
      client.send(Message.response(
        requestId,
        MessageType.HELLO,
        new Payloads.Hello(SocketProtocolConstants.PROTOCOL_VERSION)
      ));

      final Message<?> welcome = client.readMessageWithTimeout(Duration.ofSeconds(2));
      assertNotNull(welcome);
      assertEquals(MessageType.WELCOME, welcome.type());
      assertEquals(requestId, welcome.requestId());
      final Payloads.Welcome welcomePayload = (Payloads.Welcome) welcome.payload();
      assertEquals(SocketProtocolConstants.PROTOCOL_VERSION, welcomePayload.protocolVersion());

      final Message<?> status = client.readMessageWithTimeout(Duration.ofSeconds(2));
      assertNotNull(status);
      assertEquals(MessageType.INTERACTIVITY_STATUS, status.type());
      assertNull(status.requestId());
      assertFalse(((Payloads.InteractivityStatus) status.payload()).available());
    }
  }

  @Test
  void handshakeRejectsMissingRequestId() throws Exception {
    final Path socket = this.startServer();

    try (TestClient client = TestClient.connect(socket)) {
      client.send(Message.unsolicited(
        MessageType.HELLO,
        new Payloads.Hello(SocketProtocolConstants.PROTOCOL_VERSION)
      ));

      final Message<?> reject = client.readMessageWithTimeout(Duration.ofSeconds(2));
      assertNotNull(reject);
      assertEquals(MessageType.REJECT, reject.type());
      assertNull(reject.requestId());

      final Payloads.Reject payload = (Payloads.Reject) reject.payload();
      assertEquals("Missing requestId", payload.reason());
      assertEquals(SocketProtocolConstants.PROTOCOL_VERSION, payload.expectedVersion());
    }
  }

  @Test
  void handshakeRejectsNonHelloFirstMessage() throws Exception {
    final Path socket = this.startServer();

    try (TestClient client = TestClient.connect(socket)) {
      final String requestId = UUID.randomUUID().toString();
      client.send(Message.response(requestId, MessageType.PING, new Payloads.Ping()));

      final Message<?> reject = client.readMessageWithTimeout(Duration.ofSeconds(2));
      assertNotNull(reject);
      assertEquals(MessageType.REJECT, reject.type());
      assertEquals(requestId, reject.requestId());
      assertEquals("Expected HELLO", ((Payloads.Reject) reject.payload()).reason());
    }
  }

  @Test
  void handshakeRejectsUnsupportedProtocolVersion() throws Exception {
    final Path socket = this.startServer();

    try (TestClient client = TestClient.connect(socket)) {
      final String requestId = UUID.randomUUID().toString();
      client.send(Message.response(
        requestId,
        MessageType.HELLO,
        new Payloads.Hello(SocketProtocolConstants.PROTOCOL_VERSION + 1)
      ));

      final Message<?> reject = client.readMessageWithTimeout(Duration.ofSeconds(2));
      assertNotNull(reject);
      assertEquals(MessageType.REJECT, reject.type());
      assertEquals(requestId, reject.requestId());

      final Payloads.Reject payload = (Payloads.Reject) reject.payload();
      assertEquals("Unsupported protocol version", payload.reason());
      assertEquals(SocketProtocolConstants.PROTOCOL_VERSION, payload.expectedVersion());
    }
  }

  @Test
  void interactivityAndClientReadyFlow() throws Exception {
    final Path socket = this.startServer();

    try (TestClient client = TestClient.connect(socket)) {
      final String helloRequestId = UUID.randomUUID().toString();
      client.send(Message.response(
        helloRequestId,
        MessageType.HELLO,
        new Payloads.Hello(SocketProtocolConstants.PROTOCOL_VERSION)
      ));
      assertEquals(MessageType.WELCOME, client.readMessageWithTimeout(Duration.ofSeconds(2)).type());
      final Message<?> initialStatus = client.readMessageWithTimeout(Duration.ofSeconds(2));
      assertNotNull(initialStatus);
      assertFalse(((Payloads.InteractivityStatus) initialStatus.payload()).available());

      final String completionRequestId = UUID.randomUUID().toString();
      client.send(Message.response(
        completionRequestId,
        MessageType.COMPLETION_REQUEST,
        new Payloads.CompletionRequest("help", 4)
      ));
      final Message<?> unavailableError = client.readMessageWithTimeout(Duration.ofSeconds(2));
      assertNotNull(unavailableError);
      assertEquals(MessageType.ERROR, unavailableError.type());
      assertEquals(completionRequestId, unavailableError.requestId());
      assertEquals("Interactivity is currently unavailable", ((Payloads.Error) unavailableError.payload()).message());

      this.server.enableInteractivity(InteractiveConsoleHooks.builder().build());
      final Message<?> updatedStatus = client.readMessageWithTimeout(Duration.ofSeconds(2));
      assertNotNull(updatedStatus);
      assertEquals(MessageType.INTERACTIVITY_STATUS, updatedStatus.type());
      assertTrue(((Payloads.InteractivityStatus) updatedStatus.payload()).available());

      final String completionRequestId2 = UUID.randomUUID().toString();
      client.send(Message.response(
        completionRequestId2,
        MessageType.COMPLETION_REQUEST,
        new Payloads.CompletionRequest("help", 4)
      ));
      final Message<?> unsupportedError = client.readMessageWithTimeout(Duration.ofSeconds(2));
      assertNotNull(unsupportedError);
      assertEquals(MessageType.ERROR, unsupportedError.type());
      assertEquals(completionRequestId2, unsupportedError.requestId());
      assertEquals("Completions are not supported", ((Payloads.Error) unsupportedError.payload()).message());

      client.send(Message.unsolicited(MessageType.CLIENT_READY, new Payloads.ClientReady()));
      final String pingRequestId = UUID.randomUUID().toString();
      client.send(Message.response(pingRequestId, MessageType.PING, new Payloads.Ping()));
      final Message<?> pong = client.readMessageWithTimeout(Duration.ofSeconds(2));
      assertNotNull(pong);
      assertEquals(MessageType.PONG, pong.type());
      assertEquals(pingRequestId, pong.requestId());

      this.server.broadcastLog(new Payloads.LogForward(
        "test.logger",
        "INFO",
        "hello from server",
        null,
        null,
        123L,
        "Server thread"
      ));

      final Message<?> forwarded = client.readMessageWithTimeout(Duration.ofSeconds(2));
      assertNotNull(forwarded);
      assertEquals(MessageType.LOG_FORWARD, forwarded.type());
      final Payloads.LogForward payload = (Payloads.LogForward) forwarded.payload();
      assertEquals("test.logger", payload.logger());
      assertEquals("hello from server", payload.message());
    }
  }

  private Path startServer() throws Exception {
    final Path socket = this.tempDir.resolve("endermux.sock");
    this.server = new EndermuxServer(
      LayoutConfig.pattern("%msg%n", new LayoutConfig.Flags(true, false, false), "UTF-8"),
      socket,
      4
    );
    this.server.start();

    final long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
    while (!Files.exists(socket)) {
      if (System.nanoTime() > deadline) {
        throw new AssertionError("Timed out waiting for server socket to be created");
      }
      Thread.sleep(10L);
    }

    return socket;
  }

  private static final class TestClient implements AutoCloseable {
    private final SocketChannel channel;
    private final DataInputStream input;
    private final DataOutputStream output;
    private final MessageSerializer serializer = MessageSerializer.createStandard();

    private TestClient(final SocketChannel channel) throws IOException {
      this.channel = channel;
      this.input = new DataInputStream(new BufferedInputStream(Channels.newInputStream(channel)));
      this.output = new DataOutputStream(new BufferedOutputStream(Channels.newOutputStream(channel)));
    }

    static TestClient connect(final Path socket) throws IOException {
      final SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX);
      channel.connect(UnixDomainSocketAddress.of(socket));
      return new TestClient(channel);
    }

    void send(final Message<?> message) throws IOException {
      final byte[] data = this.serializer.serialize(message).getBytes(java.nio.charset.StandardCharsets.UTF_8);
      FrameCodec.writeFrame(this.output, data);
    }

    Message<?> readMessageWithTimeout(final Duration timeout) throws IOException {
      final byte[] data = TimedRead.read(
        () -> FrameCodec.readFrame(this.input),
        timeout.toMillis(),
        "Timed out waiting for test client read",
        () -> {
          try {
            this.close();
          } catch (final IOException ignored) {
          }
        },
        200L
      );
      if (data == null) {
        return null;
      }
      return this.serializer.deserialize(new String(data, java.nio.charset.StandardCharsets.UTF_8));
    }

    @Override
    public void close() throws IOException {
      this.output.close();
      this.input.close();
      this.channel.close();
    }
  }
}
