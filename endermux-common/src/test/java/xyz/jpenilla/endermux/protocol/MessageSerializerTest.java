package xyz.jpenilla.endermux.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import org.junit.jupiter.api.Test;

class MessageSerializerTest {

  private final MessageSerializer serializer = MessageSerializer.createStandard();

  @Test
  void roundTripAllMessageTypes() {
    for (final Message<?> original : sampleMessages()) {
      final String json = this.serializer.serialize(original);
      final Message<?> decoded = this.serializer.deserialize(json);

      assertNotNull(decoded, () -> "Decoded message was null for JSON: " + json);
      assertEquals(original.type(), decoded.type());
      assertEquals(original.requestId(), decoded.requestId());
      assertEquals(original.payload(), decoded.payload());
    }
  }

  @Test
  void serializeOmitsRequestIdWhenNull() {
    final Message<Payloads.Ping> message = Message.unsolicited(MessageType.PING, new Payloads.Ping());
    final String json = this.serializer.serialize(message);
    final JsonObject root = JsonParser.parseString(json).getAsJsonObject();

    assertFalse(root.has("requestId"));
  }

  @Test
  void deserializeInvalidMessagesReturnsNull() {
    assertNull(this.serializer.deserialize("{\"data\":{}}"));
    assertNull(this.serializer.deserialize("{\"type\":\"NOT_A_REAL_TYPE\",\"data\":{}}"));
    assertNull(this.serializer.deserialize("{\"type\":\"PING\",\"data\":"));
    assertNull(this.serializer.deserialize("[1,2,3]"));
  }

  private static List<Message<?>> sampleMessages() {
    final LayoutConfig.Flags flags = new LayoutConfig.Flags(true, false, true);
    final LayoutConfig.SelectorConfig selector = new LayoutConfig.SelectorConfig(
      "%msg",
      List.of(new LayoutConfig.Match("main", "%-5level %msg%n"))
    );
    final LayoutConfig layout = LayoutConfig.loggerNameSelector(selector, flags, "UTF-8");

    final Payloads.ThrowableInfo cause = new Payloads.ThrowableInfo(
      "java.lang.IllegalArgumentException",
      "bad argument",
      List.of(new Payloads.StackFrame("test.Cause", "run", "Cause.java", 9, null, null, null, null)),
      null,
      List.of()
    );
    final Payloads.ThrowableInfo throwable = new Payloads.ThrowableInfo(
      "java.lang.RuntimeException",
      "root failure",
      List.of(new Payloads.StackFrame("test.Main", "call", "Main.java", 42, null, null, null, null)),
      cause,
      List.of()
    );

    return List.of(
      Message.response("req-hello", MessageType.HELLO, new Payloads.Hello(SocketProtocolConstants.PROTOCOL_VERSION)),
      Message.response("req-complete", MessageType.COMPLETION_REQUEST, new Payloads.CompletionRequest("say he", 6)),
      Message.response("req-highlight", MessageType.SYNTAX_HIGHLIGHT_REQUEST, new Payloads.SyntaxHighlightRequest("say hi")),
      Message.response("req-parse", MessageType.PARSE_REQUEST, new Payloads.ParseRequest("say hi", 4)),
      Message.unsolicited(MessageType.COMMAND_EXECUTE, new Payloads.CommandExecute("say hi")),
      Message.response("req-ping", MessageType.PING, new Payloads.Ping()),
      Message.unsolicited(MessageType.CLIENT_READY, new Payloads.ClientReady()),
      Message.response("req-welcome", MessageType.WELCOME, new Payloads.Welcome(SocketProtocolConstants.PROTOCOL_VERSION, layout)),
      Message.response("req-reject", MessageType.REJECT, new Payloads.Reject("Unsupported protocol version", SocketProtocolConstants.PROTOCOL_VERSION)),
      Message.response("req-completion-response", MessageType.COMPLETION_RESPONSE, new Payloads.CompletionResponse(
        List.of(
          new Payloads.CompletionResponse.CandidateInfo("help", "help", "show commands"),
          new Payloads.CompletionResponse.CandidateInfo("stop", "stop", null)
        )
      )),
      Message.response("req-highlight-response", MessageType.SYNTAX_HIGHLIGHT_RESPONSE, new Payloads.SyntaxHighlightResponse("say hi", "<green>say</green> hi")),
      Message.response("req-parse-response", MessageType.PARSE_RESPONSE, new Payloads.ParseResponse(
        "hi",
        2,
        1,
        List.of("say", "hi"),
        "say hi",
        6
      )),
      Message.unsolicited(MessageType.LOG_FORWARD, new Payloads.LogForward(
        "minecraft.server",
        "INFO",
        "server started",
        null,
        throwable,
        123456789L,
        "Server thread"
      )),
      Message.response("req-pong", MessageType.PONG, new Payloads.Pong()),
      Message.response("req-error", MessageType.ERROR, new Payloads.Error("Bad request", null)),
      Message.unsolicited(MessageType.INTERACTIVITY_STATUS, new Payloads.InteractivityStatus(true))
    );
  }
}
