package com.ev07b.net;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import com.ev07b.util.CRC16;

public class EV07BFrameEncoder extends MessageToByteEncoder<byte[]> {

    private static final byte HEADER = (byte)0xAB;

    @Override
    protected void encode(ChannelHandlerContext ctx, byte[] msg, ByteBuf out) throws Exception {
        out.writeByte(HEADER);
        // length (2 bytes big endian)
        out.writeShort(msg.length);
        out.writeBytes(msg);
        int crc = CRC16.crc16Ccitt(msg) & 0xffff;
        out.writeShort(crc);
    }
}
