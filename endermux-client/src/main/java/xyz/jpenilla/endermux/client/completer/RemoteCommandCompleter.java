package xyz.jpenilla.endermux.client.completer;

import java.io.IOException;
import java.util.List;
import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.jpenilla.endermux.client.transport.SocketTransport;
import xyz.jpenilla.endermux.jline.MinecraftCandidate;
import xyz.jpenilla.endermux.protocol.Message;
import xyz.jpenilla.endermux.protocol.MessageType;
import xyz.jpenilla.endermux.protocol.Payloads;
import xyz.jpenilla.endermux.protocol.SocketProtocolConstants;

@NullMarked
public final class RemoteCommandCompleter implements Completer {

  private static final Logger LOGGER = LoggerFactory.getLogger(RemoteCommandCompleter.class);

  private final SocketTransport socketClient;

  public RemoteCommandCompleter(final SocketTransport socketClient) {
    this.socketClient = socketClient;
  }

  @Override
  public void complete(final LineReader reader, final ParsedLine line, final List<Candidate> candidates) {
    if (!this.socketClient.isConnected() || !this.socketClient.isInteractivityAvailable()) {
      return;
    }

    try {
      final Payloads.CompletionRequest requestPayload = new Payloads.CompletionRequest(line.line(), line.cursor());
      final Message<Payloads.CompletionRequest> request = this.socketClient.createRequest(
        MessageType.COMPLETION_REQUEST,
        requestPayload
      );

      final Message<?> response = this.socketClient.sendMessageAndWaitForResponse(
        request,
        MessageType.COMPLETION_RESPONSE,
        SocketProtocolConstants.COMPLETION_TIMEOUT_MS
      );

      if (response.payload() instanceof Payloads.CompletionResponse completionResponse) {
        for (final Payloads.CompletionResponse.CandidateInfo candidate : completionResponse.candidates()) {
          candidates.add(new MinecraftCandidate(
            candidate.value(),
            candidate.display(),
            null,
            candidate.description(),
            null,
            null,
            false
          ));
        }
      }
    } catch (final IOException | InterruptedException e) {
      LOGGER.debug("Failed to request completions", e);
    }
  }
}
