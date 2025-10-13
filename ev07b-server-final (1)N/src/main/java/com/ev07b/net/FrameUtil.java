package com.ev07b.net;

import com.ev07b.util.CRC16;

public final class FrameUtil {
    private static final byte HEADER = (byte) 0xAB;

    private FrameUtil() {}

    /**
     * Build a frame matching the decoder's format:
     * [0xAB][properties][lenLow][lenHigh][checksumLE][seqLE][payload...]
     * CRC16 (CCITT) is computed over payload bytes only and written little-endian.
     */
    public static byte[] buildFrame(byte properties, int sequenceId, byte[] payload) {
        if (payload == null) payload = new byte[0];
        int len = payload.length;

        int crc = CRC16.crc16Ccitt(payload) & 0xFFFF;

        // total = 1 header + 1 props + 2 len + 2 crc + 2 seq + payload
        byte[] frame = new byte[1 + 1 + 2 + 2 + 2 + len];
        int i = 0;
        frame[i++] = HEADER;
        frame[i++] = properties;
        // len low, high
        frame[i++] = (byte) (len & 0xFF);
        frame[i++] = (byte) ((len >>> 8) & 0xFF);
        // checksum LE
        frame[i++] = (byte) (crc & 0xFF);
        frame[i++] = (byte) ((crc >>> 8) & 0xFF);
        // seq LE
        frame[i++] = (byte) (sequenceId & 0xFF);
        frame[i++] = (byte) ((sequenceId >>> 8) & 0xFF);
        // payload
        System.arraycopy(payload, 0, frame, i, len);
        return frame;
    }
}

