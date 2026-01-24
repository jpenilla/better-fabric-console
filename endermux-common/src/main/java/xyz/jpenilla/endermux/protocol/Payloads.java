package xyz.jpenilla.endermux.protocol;

import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class Payloads {

  private Payloads() {
  }

  // Client -> Server payloads

  public record Hello(int protocolVersion) implements MessagePayload {
  }

  public record CompletionRequest(String command, int cursor) implements MessagePayload {
  }

  public record SyntaxHighlightRequest(String command) implements MessagePayload {
  }

  public record ParseRequest(String command, int cursor) implements MessagePayload {
  }

  public record CommandExecute(String command) implements MessagePayload {
  }

  public record Ping() implements MessagePayload {
  }

  public record ClientReady() implements MessagePayload {
  }

  public record Disconnect() implements MessagePayload {
  }

  // Server -> Client payloads

  public record Welcome(int protocolVersion, LayoutConfig logLayout) implements MessagePayload {
  }

  public record Reject(String reason, int expectedVersion) implements MessagePayload {
  }

  public record CompletionResponse(List<CandidateInfo> candidates) implements MessagePayload {
    public record CandidateInfo(String value, String display, @Nullable String description) {
    }
  }

  public record SyntaxHighlightResponse(String command, String highlighted) implements MessagePayload {
  }

  public record ParseResponse(
    String word,
    int wordCursor,
    int wordIndex,
    List<String> words,
    String line,
    int cursor
  ) implements MessagePayload {
  }

  public record CommandResponse(Status status, String command) implements MessagePayload {
    public enum Status {
      EXECUTED,
      FAILED
    }
  }

  public record LogForward(
    String logger,
    String level,
    String message,
    @Nullable String componentMessageJson,
    long timestamp,
    String thread
  ) implements MessagePayload {
  }

  public record Pong() implements MessagePayload {
  }

  public record Error(String message, @Nullable String details) implements MessagePayload {
  }

  public record ConnectionStatus(Status status) implements MessagePayload {
    public enum Status {
      CONNECTED,
      DISCONNECTED
    }
  }
}
