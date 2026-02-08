package xyz.jpenilla.endermux.protocol;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FrameCodecTest {

  @Test
  void readWriteRoundTrip() throws Exception {
    final byte[] payload = "ping".getBytes(StandardCharsets.UTF_8);
    final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    final DataOutputStream out = new DataOutputStream(bytes);

    FrameCodec.writeFrame(out, payload);

    final DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
    final byte[] decoded = FrameCodec.readFrame(in);

    assertArrayEquals(payload, decoded);
  }

  @Test
  void eofWhileReadingLengthReturnsNull() throws Exception {
    final DataInputStream in = new DataInputStream(new ByteArrayInputStream(new byte[0]));
    assertNull(FrameCodec.readFrame(in));
  }

  @Test
  void invalidFrameLengthThrowsProtocolException() {
    final ProtocolException zero = assertThrows(ProtocolException.class, () -> FrameCodec.readFrame(inputForLength(0)));
    assertEquals("Invalid frame size: 0", zero.getMessage());

    final ProtocolException negative = assertThrows(ProtocolException.class, () -> FrameCodec.readFrame(inputForLength(-1)));
    assertEquals("Invalid frame size: -1", negative.getMessage());

    final int tooLarge = SocketProtocolConstants.MAX_FRAME_SIZE_BYTES + 1;
    final ProtocolException large = assertThrows(ProtocolException.class, () -> FrameCodec.readFrame(inputForLength(tooLarge)));
    assertEquals("Invalid frame size: " + tooLarge, large.getMessage());
  }

  @Test
  void oversizeWriteThrowsProtocolException() {
    final byte[] oversize = new byte[SocketProtocolConstants.MAX_FRAME_SIZE_BYTES + 1];
    final DataOutputStream out = new DataOutputStream(new ByteArrayOutputStream());

    final ProtocolException ex = assertThrows(ProtocolException.class, () -> FrameCodec.writeFrame(out, oversize));
    assertEquals("Frame too large: " + oversize.length, ex.getMessage());
  }

  private static DataInputStream inputForLength(final int length) throws Exception {
    final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (DataOutputStream out = new DataOutputStream(bytes)) {
      out.writeInt(length);
    }
    return new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
  }
}
