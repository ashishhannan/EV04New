package com.ev07b.net;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import java.util.List;
import com.ev07b.model.EV07BMessage;
import com.ev07b.util.CRC16;

public class EV07BFrameDecoder extends ByteToMessageDecoder {

    private static final byte HEADER = (byte)0xAB;

    // Configurable offsets (some protocols include length as 2 bytes big-endian after header)
    // This implementation looks for HEADER, then expects 2-byte length (big-endian), then payload of that length,
    // finally a 2-byte CRC16 at the end of the frame (big-endian).
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        in.markReaderIndex();
        if (in.readableBytes() < 1) {
            return;
        }
        // Find HEADER
        boolean found = false;
        while (in.readableBytes() >= 1) {
            if (in.getByte(in.readerIndex()) == HEADER) {
                found = true;
                break;
            } else {
                in.readByte(); // discard
            }
        }
        if (!found) {
            return;
        }
        if (in.readableBytes() < 1 + 2) {
            // need at least header + length
            in.resetReaderIndex();
            return;
        }
        in.readByte(); // consume header
        int len = in.readUnsignedShort(); // Big-endian length
        if (in.readableBytes() < len + 2) { // payload + crc16
            in.resetReaderIndex();
            return;
        }
        byte[] payload = new byte[len];
        in.readBytes(payload);
        int crcReceived = in.readUnsignedShort();
        int crcCalc = CRC16.crc16Ccitt(payload);

        if (crcCalc != crcReceived) {
            System.out.println("[Decoder] CRC mismatch: calc=0x" + Integer.toHexString(crcCalc) + " recv=0x" + Integer.toHexString(crcReceived));
            // discard frame and continue searching
            return;
        }
        // Extract commandId and deviceId heuristically:
        int commandId = 0;
        if (payload.length > 0) {
            commandId = payload[0] & 0xff; // first byte as command id (adjust if protocol differs)
        }
        String deviceId = extractDeviceId(payload);
        EV07BMessage msg = new EV07BMessage(deviceId, commandId, payload);
        out.add(msg);
    }

    // Heuristic to find device id (IMEI-like numeric string) in payload
    private String extractDeviceId(byte[] payload) {
        // search for ASCII digits 6..20 in payload
        int consec = 0;
        StringBuilder sb = new StringBuilder();
        for (byte b : payload) {
            int c = b & 0xff;
            if (c >= '0' && c <= '9') {
                sb.append((char)c);
                consec++;
                if (consec >= 20) break;
            } else {
                if (consec >= 6 && consec <= 20) break;
                sb.setLength(0);
                consec = 0;
            }
        }
        if (sb.length() >= 6) return sb.toString();
        return "";
    }
}
