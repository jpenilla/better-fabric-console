package xyz.jpenilla.endermux.protocol;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import java.util.Map;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class MessageSerializer {

  private static final Gson GSON = new GsonBuilder()
    .serializeNulls()
    .create();

  private static final Map<MessageType, Class<? extends MessagePayload>> TYPE_TO_PAYLOAD = Map.ofEntries(
    // Client -> Server
    Map.entry(MessageType.HELLO, Payloads.Hello.class),
    Map.entry(MessageType.COMPLETION_REQUEST, Payloads.CompletionRequest.class),
    Map.entry(MessageType.SYNTAX_HIGHLIGHT_REQUEST, Payloads.SyntaxHighlightRequest.class),
    Map.entry(MessageType.PARSE_REQUEST, Payloads.ParseRequest.class),
    Map.entry(MessageType.COMMAND_EXECUTE, Payloads.CommandExecute.class),
    Map.entry(MessageType.PING, Payloads.Ping.class),
    Map.entry(MessageType.CLIENT_READY, Payloads.ClientReady.class),
    // Server -> Client
    Map.entry(MessageType.WELCOME, Payloads.Welcome.class),
    Map.entry(MessageType.REJECT, Payloads.Reject.class),
    Map.entry(MessageType.COMPLETION_RESPONSE, Payloads.CompletionResponse.class),
    Map.entry(MessageType.SYNTAX_HIGHLIGHT_RESPONSE, Payloads.SyntaxHighlightResponse.class),
    Map.entry(MessageType.PARSE_RESPONSE, Payloads.ParseResponse.class),
    Map.entry(MessageType.COMMAND_RESPONSE, Payloads.CommandResponse.class),
    Map.entry(MessageType.LOG_FORWARD, Payloads.LogForward.class),
    Map.entry(MessageType.PONG, Payloads.Pong.class),
    Map.entry(MessageType.ERROR, Payloads.Error.class),
    Map.entry(MessageType.INTERACTIVITY_STATUS, Payloads.InteractivityStatus.class)
  );

  public static MessageSerializer createStandard() {
    return new MessageSerializer();
  }

  public String serialize(final Message<?> message) {
    final JsonObject root = new JsonObject();
    root.addProperty("type", message.type().name());

    if (message.requestId() != null) {
      root.addProperty("requestId", message.requestId());
    }

    root.add("data", GSON.toJsonTree(message.payload()));

    return root.toString();
  }

  public @Nullable Message<?> deserialize(final String json) {
    try {
      final JsonObject root = JsonParser.parseString(json).getAsJsonObject();

      if (!root.has("type")) {
        return null;
      }

      final String typeName = root.get("type").getAsString();
      final MessageType type;
      try {
        type = MessageType.valueOf(typeName);
      } catch (final IllegalArgumentException e) {
        return null;
      }

      final String requestId = root.has("requestId") && !root.get("requestId").isJsonNull()
        ? root.get("requestId").getAsString()
        : null;

      final Class<? extends MessagePayload> payloadClass = TYPE_TO_PAYLOAD.get(type);
      if (payloadClass == null) {
        return null;
      }

      final JsonObject data = root.has("data") && root.get("data").isJsonObject()
        ? root.getAsJsonObject("data")
        : new JsonObject();
      final MessagePayload payload = GSON.fromJson(data, payloadClass);

      return new Message<>(type, requestId, payload);

    } catch (final JsonSyntaxException | IllegalStateException | ClassCastException e) {
      return null;
    }
  }
}
