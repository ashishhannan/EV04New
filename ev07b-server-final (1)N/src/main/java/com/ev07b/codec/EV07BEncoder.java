package com.ev07b.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import com.ev07b.model.EV07BMessage;
import com.ev07b.util.CRC16;

/**
 * EV07BEncoder - encodes EV07BMessage into framed bytes:
 * [0xAB][len:2 BE][payload][crc16:2 BE]
 *
 * Updated to use EV07BMessage.getPayload() after refactor removed the 'properties' field.
 */
public class EV07BEncoder extends MessageToByteEncoder<EV07BMessage> {

    private static final byte HEADER = (byte) 0xAB;

    @Override
    protected void encode(ChannelHandlerContext ctx, EV07BMessage msg, ByteBuf out) throws Exception {
        // Use refactored getter
        byte[] payload = msg != null ? msg.getPayload() : null;
        if (payload == null) payload = new byte[0];

        // Frame: header, 2-byte length (big-endian), payload, 2-byte CRC (big-endian)
        out.writeByte(HEADER);
        out.writeShort(payload.length); // length
        out.writeBytes(payload);

        int crc = CRC16.crc16Ccitt(payload) & 0xFFFF;
        out.writeShort(crc);
    }
}