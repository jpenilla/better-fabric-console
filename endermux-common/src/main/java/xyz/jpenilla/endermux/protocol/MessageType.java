package xyz.jpenilla.endermux.protocol;

import org.jspecify.annotations.NullMarked;

@NullMarked
public enum MessageType {
  // Client -> Server
  HELLO(Direction.CLIENT_TO_SERVER, true),
  COMPLETION_REQUEST(Direction.CLIENT_TO_SERVER, true),
  SYNTAX_HIGHLIGHT_REQUEST(Direction.CLIENT_TO_SERVER, true),
  PARSE_REQUEST(Direction.CLIENT_TO_SERVER, true),
  COMMAND_EXECUTE(Direction.CLIENT_TO_SERVER, false),
  PING(Direction.CLIENT_TO_SERVER, true),
  CLIENT_READY(Direction.CLIENT_TO_SERVER, false),
  DISCONNECT(Direction.CLIENT_TO_SERVER, false),

  // Server -> Client
  WELCOME(Direction.SERVER_TO_CLIENT, false),
  REJECT(Direction.SERVER_TO_CLIENT, false),
  COMPLETION_RESPONSE(Direction.SERVER_TO_CLIENT, false),
  SYNTAX_HIGHLIGHT_RESPONSE(Direction.SERVER_TO_CLIENT, false),
  PARSE_RESPONSE(Direction.SERVER_TO_CLIENT, false),
  COMMAND_RESPONSE(Direction.SERVER_TO_CLIENT, false),
  LOG_FORWARD(Direction.SERVER_TO_CLIENT, false),
  PONG(Direction.SERVER_TO_CLIENT, false),
  ERROR(Direction.SERVER_TO_CLIENT, false),
  CONNECTION_STATUS(Direction.SERVER_TO_CLIENT, false);

  private final Direction direction;
  private final boolean requiresResponse;

  MessageType(final Direction direction, final boolean requiresResponse) {
    this.direction = direction;
    this.requiresResponse = requiresResponse;
  }

  public Direction direction() {
    return this.direction;
  }

  public boolean requiresResponse() {
    return this.requiresResponse;
  }

  public boolean isClientToServer() {
    return this.direction == Direction.CLIENT_TO_SERVER;
  }

  public boolean isServerToClient() {
    return this.direction == Direction.SERVER_TO_CLIENT;
  }

  public static MessageType responseTypeForPayload(final MessagePayload payload) {
    return switch (payload) {
      case Payloads.Welcome ignored -> MessageType.WELCOME;
      case Payloads.Reject ignored -> MessageType.REJECT;
      // Server -> Client response payloads
      case Payloads.CompletionResponse ignored -> MessageType.COMPLETION_RESPONSE;
      case Payloads.SyntaxHighlightResponse ignored -> MessageType.SYNTAX_HIGHLIGHT_RESPONSE;
      case Payloads.CommandResponse ignored -> MessageType.COMMAND_RESPONSE;
      case Payloads.LogForward ignored -> MessageType.LOG_FORWARD;
      case Payloads.Pong ignored -> MessageType.PONG;
      case Payloads.Error ignored -> MessageType.ERROR;
      case Payloads.ConnectionStatus ignored -> MessageType.CONNECTION_STATUS;
      case Payloads.ParseResponse ignored -> MessageType.PARSE_RESPONSE;
      // Client -> Server request payloads should never be sent as responses
      case Payloads.Hello _,
           Payloads.CompletionRequest _,
           Payloads.SyntaxHighlightRequest _,
           Payloads.CommandExecute _,
           Payloads.Ping _,
           Payloads.ClientReady _,
           Payloads.ParseRequest _,
           Payloads.Disconnect _ -> throw new IllegalArgumentException("Cannot send request payload as response: " + payload.getClass().getSimpleName());
      default -> throw new IllegalArgumentException("Unknown payload type: " + payload.getClass().getSimpleName());
    };
  }

  public enum Direction {
    CLIENT_TO_SERVER,
    SERVER_TO_CLIENT
  }
}
