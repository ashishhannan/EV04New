package com.ev07b.entities;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "pending_command")
public class PendingCommandEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id")
    private String deviceId;

    @Lob
    private byte[] payload;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    public PendingCommandEntity() {}

    public PendingCommandEntity(String deviceId, byte[] payload) {
        this.deviceId = deviceId;
        this.payload = payload;
    }

    public Long getId() { return id; }
    public String getDeviceId() { return deviceId; }
    public byte[] getPayload() { return payload; }
    public Instant getCreatedAt() { return createdAt; }
}
