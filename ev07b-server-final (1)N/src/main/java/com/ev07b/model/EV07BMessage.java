package com.ev07b.model;

import java.time.Instant;

public class EV07BMessage {
    private final String deviceId;
    private final int commandId;
    private final byte[] payload;
    private final Instant receivedAt;

    public EV07BMessage(String deviceId, int commandId, byte[] payload) {
        this.deviceId = deviceId;
        this.commandId = commandId;
        this.payload = payload;
        this.receivedAt = Instant.now();
    }

    public String getDeviceId() { return deviceId; }
    public int getCommandId() { return commandId; }
    public byte[] getPayload() { return payload; }
    public Instant getReceivedAt() { return receivedAt; }
}
