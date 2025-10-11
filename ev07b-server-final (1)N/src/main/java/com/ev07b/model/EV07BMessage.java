package com.ev07b.model;

import java.time.Instant;

public class EV07BMessage {
    private final String deviceId;
    private final int commandId;
    private final byte[] payload;
    private final Instant receivedAt;

    // New: carry protocol-level metadata
    private final byte properties;
    private final int sequenceId;

    public EV07BMessage(String deviceId, int commandId, byte[] payload) {
        this(deviceId, commandId, payload, (byte)0x00, 0);
    }

    public EV07BMessage(String deviceId, int commandId, byte[] payload, byte properties, int sequenceId) {
        this.deviceId = deviceId;
        this.commandId = commandId;
        this.payload = payload;
        this.properties = properties;
        this.sequenceId = sequenceId;
        this.receivedAt = Instant.now();
    }

    public String getDeviceId() { return deviceId; }
    public int getCommandId() { return commandId; }
    public byte[] getPayload() { return payload; }
    public Instant getReceivedAt() { return receivedAt; }

    public byte getProperties() { return properties; }
    public int getSequenceId() { return sequenceId; }
}
