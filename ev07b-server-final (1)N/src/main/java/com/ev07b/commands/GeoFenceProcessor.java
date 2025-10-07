package com.ev07b.commands;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import io.netty.channel.Channel;
import com.ev07b.model.EV07BMessage;
import com.ev07b.repos.GeofenceRepository;
import com.ev07b.repos.CommandLogRepository;
import com.ev07b.entities.GeofenceEntity;
import com.ev07b.entities.CommandLogEntity;
import com.ev07b.services.CommandService;
import com.ev07b.services.DeviceService;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * GeoFenceProcessor
 *
 * Note: The EV-07B protocol document contains the exact byte layout for GeoFence messages.
 * This implementation parses a simple format: first byte indicates number of points,
 * followed by pairs of 4-byte lat (int32, scaled by 1e6) and 4-byte lon (int32, scaled by 1e6).
 *
 * IMPORTANT: If the protocol uses a different layout (flags, radius, type fields, sequence ids),
 * update parseGeoPayload() accordingly using the protocol doc. Placeholders and TODOs are left
 * where precise implementation is required.
 */
@Component
public class GeoFenceProcessor implements CommandProcessor {

    private static final int GEOFENCE_CMD = 0x51;

    @Autowired
    private GeofenceRepository geofenceRepo;

    @Autowired
    private CommandLogRepository logRepo;

    @Autowired
    private CommandService commandService;

    @Autowired
    private DeviceService deviceService;

    @Autowired
    private DeviceConnectionManager connMgr;

    @Override
    public int commandId() {
        return GEOFENCE_CMD;
    }

    @Override
    public void handle(EV07BMessage msg, Channel ch) {
        String deviceId = msg.getDeviceId();
        byte[] payload = msg.getPayload();

        // mark device last seen/connected
        deviceService.touch(deviceId);

        // Save a log entry
        logRepo.save(new CommandLogEntity(deviceId, msg.getCommandId(), payload));

        // Parse geofence(s) from payload (this will vary depending on actual protocol)
        List<String> parsed = parseGeoPayload(payload);

        // Persist a GeofenceEntity for now with raw payload and a simple name
        GeofenceEntity g = new GeofenceEntity(deviceId, "geofence-" + System.currentTimeMillis(), payload);
        geofenceRepo.save(g);

        System.out.println("[GeoFenceProcessor] Parsed geofence items: " + parsed.size());

        // Example: send ACK according to protocol (placeholder)
        byte[] ack = buildAck(msg);
        if (ch != null && ch.isActive()) {
            ch.writeAndFlush(io.netty.buffer.Unpooled.wrappedBuffer(ack));
        } else {
            // If not connected, queue ack to be sent when device reconnects
            commandService.queuePending(deviceId, ack);
        }
    }

    private List<String> parseGeoPayload(byte[] payload) {
        List<String> out = new ArrayList<>();
        if (payload == null || payload.length == 0) return out;

        try {
            ByteBuffer bb = ByteBuffer.wrap(payload);
            bb.order(java.nio.ByteOrder.BIG_ENDIAN); // adjust per protocol

            int remaining = bb.remaining();
            if (remaining < 1) return out;
            int n = Byte.toUnsignedInt(bb.get());
            for (int i = 0; i < n; i++) {
                if (bb.remaining() < 8) break;
                int lat_i = bb.getInt();
                int lon_i = bb.getInt();
                double lat = lat_i / 1e6;
                double lon = lon_i / 1e6;
                out.add(lat + "," + lon);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return out;
    }

    private byte[] buildAck(EV07BMessage msg) {
        // TODO: build protocol-correct ACK frame including header, length, seq, CRC16, etc.
        // For now return a minimal placeholder frame. Replace with exact framing per EV-07B doc.
        return new byte[] {(byte)0xAB, 0x01, (byte)msg.getCommandId()};
    }
}
