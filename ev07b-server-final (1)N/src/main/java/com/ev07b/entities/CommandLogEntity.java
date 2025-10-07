package com.ev07b.entities;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "command_log")
public class CommandLogEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id")
    private String deviceId;

    @Column(name = "command_id")
    private Integer commandId;

    @Lob
    @Column(name = "payload", columnDefinition = "bytea")
    private byte[] payload;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    public CommandLogEntity() {}

    public CommandLogEntity(String deviceId, Integer commandId, byte[] payload) {
        this.deviceId = deviceId;
        this.commandId = commandId;
        this.payload = payload;
    }

    public Long getId() { return id; }
    public String getDeviceId() { return deviceId; }
    public Integer getCommandId() { return commandId; }
    public byte[] getPayload() { return payload; }
    public Instant getCreatedAt() { return createdAt; }
}
