package com.ev07b.entities;

import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "geofence")
public class GeofenceEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id")
    private String deviceId;

    @Column(name = "name")
    private String name;

    // Use bytea mapping (binary) instead of OID/BLOB
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "payload")
    private byte[] payload;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    public GeofenceEntity() {}

    public GeofenceEntity(String deviceId, String name, byte[] payload) {
        this.deviceId = deviceId;
        this.name = name;
        this.payload = payload;
    }

    public Long getId() { return id; }
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public byte[] getPayload() { return payload; }
    public void setPayload(byte[] payload) { this.payload = payload; }
    public Instant getCreatedAt() { return createdAt; }
}
