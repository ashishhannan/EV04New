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

        byte properties = in.readByte(); // protocol flag bits
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
        String deviceId = extractDeviceIdFromKeys(bodyBytes);
        if (deviceId == null || deviceId.isEmpty() || "UNKNOWN".equalsIgnoreCase(deviceId)) {
            deviceId = scanAsciiDigits(bodyBytes);
            if (deviceId == null) deviceId = "UNKNOWN";
        }

        EV07BMessage message = new EV07BMessage(deviceId, commandId, bodyBytes, properties, seqId);
        out.add(message);
    }

    /**
     * Extract Device ID (Key 0x01) according to protocol key layout.
     * Layout: [command][ keyLen ][ key ][ value... ] ...
     * For Device ID key: keyLen=0x10, key=0x01, value=15 ASCII digits.
     */
    private String extractDeviceIdFromKeys(byte[] body) {
        if (body == null || body.length < 1) return "UNKNOWN";
        int i = 1; // skip command byte
        try {
            while (i < body.length) {
                int keyLen = Byte.toUnsignedInt(body[i++]);
                if (keyLen < 1) break; // must include at least key byte
                if (i >= body.length) break;
                int key = Byte.toUnsignedInt(body[i++]);
                int valueLen = keyLen - 1;
                if (valueLen < 0) break;
                if (i + valueLen > body.length) break;

                if (key == 0x01 && valueLen >= 8) {
                    // Expect 15 ASCII digits for IMEI by spec (valueLen typically 15)
                    int n = Math.min(15, valueLen);
                    StringBuilder sb = new StringBuilder(15);
                    for (int k = 0; k < n; k++) {
                        int b = body[i + k] & 0xFF;
                        if (b >= '0' && b <= '9') {
                            sb.append((char) b);
                        } else {
                            // non-digit encountered; abort this key
                            sb.setLength(0);
                            break;
                        }
                    }
                    if (sb.length() >= 6) {
                        return sb.toString();
                    }
                }
                i += valueLen; // advance to next key body
            }
        } catch (Exception ignore) {
        }
        return "UNKNOWN";
    }

    // Fallback: scan contiguous ASCII digits sequence length>=10 as IMEI-like string
    private String scanAsciiDigits(byte[] body) {
        if (body == null) return null;
        int bestStart = -1, bestLen = 0, curStart = -1, curLen = 0;
        for (int i = 0; i < body.length; i++) {
            int b = body[i] & 0xFF;
            if (b >= '0' && b <= '9') {
                if (curLen == 0) curStart = i;
                curLen++;
            } else {
                if (curLen > bestLen) { bestLen = curLen; bestStart = curStart; }
                curLen = 0; curStart = -1;
            }
        }
        if (curLen > bestLen) { bestLen = curLen; bestStart = curStart; }
        if (bestLen >= 10 && bestStart >= 0) {
            int n = Math.min(15, bestLen);
            return new String(body, bestStart, n);
        }
        return null;
    }
}
