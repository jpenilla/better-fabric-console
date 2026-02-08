package xyz.jpenilla.endermux.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class MessageTypeTest {

  @Test
  void responseTypeForResponsePayloads() {
    assertEquals(MessageType.WELCOME, MessageType.responseTypeForPayload(new Payloads.Welcome(8, LayoutConfig.pattern(
      "%msg%n",
      new LayoutConfig.Flags(true, false, false),
      null
    ))));
    assertEquals(MessageType.REJECT, MessageType.responseTypeForPayload(new Payloads.Reject("bad", 8)));
    assertEquals(MessageType.COMPLETION_RESPONSE, MessageType.responseTypeForPayload(new Payloads.CompletionResponse(java.util.List.of())));
    assertEquals(MessageType.SYNTAX_HIGHLIGHT_RESPONSE, MessageType.responseTypeForPayload(new Payloads.SyntaxHighlightResponse("cmd", "hl")));
    assertEquals(MessageType.PARSE_RESPONSE, MessageType.responseTypeForPayload(new Payloads.ParseResponse("w", 0, 0, java.util.List.of(), "line", 0)));
    assertEquals(MessageType.LOG_FORWARD, MessageType.responseTypeForPayload(new Payloads.LogForward("logger", "INFO", "msg", null, null, 0L, "thread")));
    assertEquals(MessageType.PONG, MessageType.responseTypeForPayload(new Payloads.Pong()));
    assertEquals(MessageType.ERROR, MessageType.responseTypeForPayload(new Payloads.Error("oops", null)));
    assertEquals(MessageType.INTERACTIVITY_STATUS, MessageType.responseTypeForPayload(new Payloads.InteractivityStatus(true)));
  }

  @Test
  void responseTypeForRequestPayloadsThrows() {
    assertThrows(IllegalArgumentException.class, () -> MessageType.responseTypeForPayload(new Payloads.Hello(8)));
    assertThrows(IllegalArgumentException.class, () -> MessageType.responseTypeForPayload(new Payloads.CompletionRequest("cmd", 0)));
    assertThrows(IllegalArgumentException.class, () -> MessageType.responseTypeForPayload(new Payloads.SyntaxHighlightRequest("cmd")));
    assertThrows(IllegalArgumentException.class, () -> MessageType.responseTypeForPayload(new Payloads.ParseRequest("cmd", 0)));
    assertThrows(IllegalArgumentException.class, () -> MessageType.responseTypeForPayload(new Payloads.CommandExecute("cmd")));
    assertThrows(IllegalArgumentException.class, () -> MessageType.responseTypeForPayload(new Payloads.Ping()));
    assertThrows(IllegalArgumentException.class, () -> MessageType.responseTypeForPayload(new Payloads.ClientReady()));
  }
}
