package com.ev07b.codec;

import com.ev07b.model.EV07BMessage;
import com.ev07b.util.CRC16;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import java.util.List;

/**
 * EV07BFrameDecoder
 *
 * Decodes raw EV07B (EV04-compatible) frames:
 * [0xAB][properties][lenLow][lenHigh][checksumLE][seqLE][payload...]
 */
public class EV07BFrameDecoder extends ByteToMessageDecoder {

    private static final byte HEADER = (byte) 0xAB;

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        in.markReaderIndex();

        if (in.readableBytes() < 1) {
            return; // not enough for header
        }

        byte header = in.readByte();
        if (header != HEADER) {
            // skip until we find header byte
            boolean found = false;
            while (in.isReadable()) {
                if (in.readByte() == HEADER) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return;
            }
        }

        // Ensure we have minimum bytes for header + metadata
        if (in.readableBytes() < 1 + 2 + 2 + 2) {
            in.resetReaderIndex();
            return;
        }

        byte properties = in.readByte(); // protocol flag bits, not used here
        int lenLow = in.readUnsignedByte();
        int lenHigh = in.readUnsignedByte();
        int bodyLen = (lenHigh << 8) | lenLow;

        if (bodyLen < 0 || bodyLen > 4096) {
            ctx.fireExceptionCaught(new IllegalArgumentException("Invalid bodyLen=" + bodyLen));
            return;
        }

        if (in.readableBytes() < 2 + 2 + bodyLen) {
            in.resetReaderIndex();
            return;
        }

        int checksum = in.readUnsignedShortLE();
        int seqId = in.readUnsignedShortLE();
        byte[] bodyBytes = new byte[bodyLen];
        in.readBytes(bodyBytes);

        // Compute CRC16 (CCITT) for payload
        int calculated = CRC16.crc16Ccitt(bodyBytes) & 0xFFFF;
        if (calculated != (checksum & 0xFFFF)) {
            System.err.printf("CRC mismatch: calc=%04X recv=%04X%n", calculated, checksum);
            // optionally fire event or discard
            return;
        }

        // Derive fields for EV07BMessage
        int commandId = (bodyBytes.length > 0) ? (bodyBytes[0] & 0xFF) : 0;
        String deviceId = extractDeviceId(bodyBytes);

        EV07BMessage message = new EV07BMessage(deviceId, commandId, bodyBytes);
        out.add(message);
    }

    /**
     * Attempt to extract a numeric device ID (IMEI) embedded in the payload.
     * Adjust logic as needed for your EV04 message layout.
     */
    private String extractDeviceId(byte[] body) {
        StringBuilder sb = new StringBuilder();
        for (byte b : body) {
            int c = b & 0xFF;
            if (c >= '0' && c <= '9') {
                sb.append((char) c);
                if (sb.length() >= 15) break; // typical IMEI length
            } else {
                if (sb.length() >= 6) break;
                sb.setLength(0);
            }
        }
        return sb.length() > 0 ? sb.toString() : "UNKNOWN";
    }
}
