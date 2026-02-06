package xyz.jpenilla.endermux.client.parser;

import java.io.IOException;
import org.jline.reader.ParsedLine;
import org.jline.reader.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.jpenilla.endermux.client.transport.SocketTransport;
import xyz.jpenilla.endermux.protocol.Message;
import xyz.jpenilla.endermux.protocol.MessageType;
import xyz.jpenilla.endermux.protocol.Payloads;
import xyz.jpenilla.endermux.protocol.SocketProtocolConstants;

public final class RemoteParser implements Parser {
  private static final Logger LOGGER = LoggerFactory.getLogger(RemoteParser.class);
  private final SocketTransport socketClient;

  public RemoteParser(final SocketTransport socketClient) {
    this.socketClient = socketClient;
  }

  @Override
  public ParsedLine parse(final String line, final int cursor, final ParseContext context) {
    if (!this.socketClient.isConnected() || !this.socketClient.isInteractivityAvailable()) {
      return new RemoteParsedLine("", 0, 0, java.util.List.of(), line, cursor);
    }

    try {
      final Payloads.ParseRequest requestPayload = new Payloads.ParseRequest(line, cursor);
      final Message<Payloads.ParseRequest> request = this.socketClient.createRequest(
        MessageType.PARSE_REQUEST,
        requestPayload
      );

      final Message<?> response = this.socketClient.sendMessageAndWaitForResponse(
        request,
        MessageType.PARSE_RESPONSE,
        SocketProtocolConstants.COMPLETION_TIMEOUT_MS
      );

      if (response.payload() instanceof Payloads.ParseResponse parseResponse) {
        return new RemoteParsedLine(
          parseResponse.word(),
          parseResponse.wordCursor(),
          parseResponse.wordIndex(),
          parseResponse.words(),
          parseResponse.line(),
          parseResponse.cursor()
        );
      }
    } catch (final IOException | InterruptedException e) {
      LOGGER.debug("Failed to request parse data", e);
    }
    return new RemoteParsedLine("", 0, 0, java.util.List.of(), line, cursor);
  }

  @Override
  public boolean isEscapeChar(final char ch) {
    return false;
  }
}
