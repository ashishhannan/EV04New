package com.ev07b.util;

public class CRC16 {
    private final int poly;
    private final int init;
    private final int xorOut;

    public CRC16() {
        // Default: CRC-CCITT (XModem) polynomial 0x1021, init 0x0000
        this.poly = 0x1021;
        this.init = 0x0000;
        this.xorOut = 0x0000;
    }

    public CRC16(int poly, int init, int xorOut) {
        this.poly = poly;
        this.init = init;
        this.xorOut = xorOut;
    }

    public int compute(byte[] data) {
        int crc = init & 0xffff;
        for (byte b : data) {
            crc ^= (b & 0xff) << 8;
            for (int i = 0; i < 8; i++) {
                if ((crc & 0x8000) != 0) {
                    crc = ((crc << 1) ^ poly) & 0xffff;
                } else {
                    crc = (crc << 1) & 0xffff;
                }
            }
        }
        return crc ^ xorOut;
    }

    // convenience static method
    public static int crc16Ccitt(byte[] data) {
        return new CRC16(0x1021, 0x0000, 0x0000).compute(data);
    }
}
