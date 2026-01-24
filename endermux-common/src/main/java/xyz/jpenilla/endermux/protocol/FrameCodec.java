package xyz.jpenilla.endermux.protocol;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class FrameCodec {

  private FrameCodec() {
  }

  public static byte @Nullable [] readFrame(final DataInputStream in) throws IOException {
    final int length;
    try {
      length = in.readInt();
    } catch (final IOException e) {
      return null;
    }

    if (length <= 0 || length > SocketProtocolConstants.MAX_FRAME_SIZE_BYTES) {
      throw new ProtocolException("Invalid frame size: " + length);
    }

    final byte[] data = new byte[length];
    in.readFully(data);
    return data;
  }

  public static void writeFrame(final DataOutputStream out, final byte[] data) throws IOException {
    if (data.length > SocketProtocolConstants.MAX_FRAME_SIZE_BYTES) {
      throw new ProtocolException("Frame too large: " + data.length);
    }

    final ByteBuffer header = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(data.length);
    out.write(header.array());
    out.write(data);
    out.flush();
  }
}
