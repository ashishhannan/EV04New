package com.ev07b.entities;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "device")
public class DeviceEntity {
    @Id
    private String id;

    @Column(name = "last_seen")
    private Instant lastSeen;

    @Column(name = "connected")
    private Boolean connected;

    public DeviceEntity() {}

    public DeviceEntity(String id) { this.id = id; this.connected = false; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public Instant getLastSeen() { return lastSeen; }
    public void setLastSeen(Instant lastSeen) { this.lastSeen = lastSeen; }

    public Boolean getConnected() { return connected; }
    public void setConnected(Boolean connected) { this.connected = connected; }
}
