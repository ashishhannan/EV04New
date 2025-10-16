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
import com.ev07b.net.FrameUtil;
import com.ev07b.commands.DeviceConnectionManager;

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
        System.out.println("[GeoFenceProcessor] Saved geofence for device " + deviceId + ", points=" + parsed.size());

        System.out.println("[GeoFenceProcessor] Parsed geofence items: " + parsed.size());
        if (!parsed.isEmpty()) {
            for (int i = 0; i < parsed.size(); i++) {
                System.out.println("  point[" + i + "]: " + parsed.get(i));
            }
        }

        // Send protocol-correct ACK frame back to device
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
            // Protocol doc: little-endian overall
            bb.order(java.nio.ByteOrder.LITTLE_ENDIAN);

            // Must start with command 0x51 in our design
            if (bb.remaining() < 1) return out;
            int first = Byte.toUnsignedInt(bb.get());
            if (first != GEOFENCE_CMD) {
                // Backward compatibility: legacy format [n][pointsBE]
                return parseLegacyBigEndian(payload);
            }

            if (bb.remaining() < 4) return out; // need flags
            int flags = bb.getInt(); // LE

            // Decode flags per doc
            int index = (flags) & 0x0F;
            int pointsBits = (flags >>> 4) & 0x0F;
            boolean enable = ((flags >>> 8) & 0x01) == 1;
            int direction = (flags >>> 9) & 0x01; // 0=out, 1=in
            int type = (flags >>> 10) & 0x01;     // 0=circle, 1=polygon
            int radius = (flags >>> 16) & 0xFFFF; // meters

            System.out.println("[GeoFenceProcessor] flags index=" + index
                    + " pointsBits=" + pointsBits
                    + " enable=" + enable
                    + " direction=" + direction
                    + " type=" + (type==0?"circle":"polygon")
                    + " radius=" + radius + "m");

            // Determine number of points
            int rem = bb.remaining();
            int n;
            if (rem % 8 == 0) {
                // No explicit count; infer from remaining
                n = rem / 8;
            } else {
                // Expect an explicit count byte, then pairs
                if (rem < 1) return out;
                n = Byte.toUnsignedInt(bb.get());
                rem = bb.remaining();
                if (rem < n * 8) n = Math.min(n, rem / 8);
            }

            for (int i = 0; i < n; i++) {
                if (bb.remaining() < 8) break;
                int lat_i = bb.getInt();   // LE
                int lon_i = bb.getInt();   // LE
                double lat = lat_i / 1e6;
                double lon = lon_i / 1e6;
                out.add(lat + "," + lon);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return out;
    }

    private List<String> parseLegacyBigEndian(byte[] payload) {
        List<String> out = new ArrayList<>();
        try {
            ByteBuffer bb = ByteBuffer.wrap(payload);
            bb.order(java.nio.ByteOrder.BIG_ENDIAN);
            if (bb.remaining() < 1) return out;
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
        // ACK payload echoes the command with status OK (0x01)
        byte[] ackPayload = new byte[] { (byte) GEOFENCE_CMD, 0x01 };

        // Echo properties and sequence if provided; otherwise default to 0x10 (ACK requested bit) and seq 0
        byte properties = msg.getProperties();
        if (properties == 0) properties = 0x10;
        int seq = msg.getSequenceId();

        return FrameUtil.buildFrame(properties, seq, ackPayload);
    }
}
