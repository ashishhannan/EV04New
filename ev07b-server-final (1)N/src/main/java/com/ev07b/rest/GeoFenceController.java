package com.ev07b.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import com.ev07b.repos.GeofenceRepository;
import com.ev07b.entities.GeofenceEntity;
import com.ev07b.commands.DeviceConnectionManager;
import com.ev07b.services.CommandService;
import com.ev07b.net.FrameUtil;
import com.ev07b.net.SequenceManager;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api/geofences")
public class GeoFenceController {

    private static final Logger log = LoggerFactory.getLogger(GeoFenceController.class);
    private static final byte CMD_GEOFENCE = (byte) 0x51;

    @Autowired
    private GeofenceRepository geofenceRepo;

    @Autowired
    private DeviceConnectionManager connMgr;

    @Autowired
    private CommandService commandService;

    @Autowired
    private SequenceManager sequenceManager;

    @GetMapping("/{deviceId}")
    public List<GeofenceEntity> listForDevice(@PathVariable String deviceId) {
        return geofenceRepo.findByDeviceId(deviceId);
    }

    @PostMapping("/{deviceId}")
    public GeofenceEntity create(@PathVariable String deviceId, @RequestBody Map<String,Object> body) {
        // Only accept radius from user
        int radius = 100; // default
        if (body.containsKey("radius")) {
            Object r = body.get("radius");
            if (r instanceof Number) radius = ((Number) r).intValue();
            else if (r instanceof String) radius = Integer.parseInt((String) r);
        }
        // Build flag: circle (bit 0), enable (bit 8), radius (bits 16-31)
        int flag = (1 << 8) | (radius << 16); // circle, enable, radius
        // For demo, use dummy lat/lon (0)
        int lat = 0;
        int lon = 0;
        byte[] payload = new byte[1 + 4 + 4 + 4]; // key + flag + lat + lon
        payload[0] = CMD_GEOFENCE;
        payload[1] = (byte) ((flag >> 24) & 0xFF);
        payload[2] = (byte) ((flag >> 16) & 0xFF);
        payload[3] = (byte) ((flag >> 8) & 0xFF);
        payload[4] = (byte) (flag & 0xFF);
        payload[5] = (byte) ((lat >> 24) & 0xFF);
        payload[6] = (byte) ((lat >> 16) & 0xFF);
        payload[7] = (byte) ((lat >> 8) & 0xFF);
        payload[8] = (byte) (lat & 0xFF);
        payload[9] = (byte) ((lon >> 24) & 0xFF);
        payload[10] = (byte) ((lon >> 16) & 0xFF);
        payload[11] = (byte) ((lon >> 8) & 0xFF);
        payload[12] = (byte) (lon & 0xFF);

        GeofenceEntity g = new GeofenceEntity(deviceId, "geofence-" + System.currentTimeMillis(), payload);
        GeofenceEntity saved = geofenceRepo.save(g);

        // Build device-facing framed message: [cmd=0x51][payload...] wrapped as protocol frame
        byte[] bodyBytes = payload;
        byte properties = 0x10; // request ACK or set flags per protocol as needed
        int seq = sequenceManager.next(deviceId);
        byte[] frame = FrameUtil.buildFrame(properties, seq, bodyBytes);

        io.netty.channel.Channel ch = connMgr.getChannel(deviceId);
        if (ch != null && ch.isActive()) {
            log.info("Sending framed geofence to device {} seq={}", deviceId, seq);
            ch.writeAndFlush(io.netty.buffer.Unpooled.wrappedBuffer(frame));
        } else {
            log.info("Device offline; queueing framed geofence for {} seq={}", deviceId, seq);
            commandService.queuePending(deviceId, frame);
        }
        return saved;
    }

    @PostMapping("/{deviceId}/send/{geofenceId}")
    public Map<String,Object> send(@PathVariable String deviceId, @PathVariable Long geofenceId) {
        Map<String,Object> res = new HashMap<>();
        GeofenceEntity g = geofenceRepo.findById(geofenceId).orElse(null);
        if (g == null) {
            res.put("sent", false);
            res.put("reason", "not found");
            return res;
        }

        // Build framed body with commandId prefix
        byte[] pl = g.getPayload();
        byte[] bodyBytes = new byte[1 + (pl == null ? 0 : pl.length)];
        bodyBytes[0] = CMD_GEOFENCE;
        if (pl != null && pl.length > 0) System.arraycopy(pl, 0, bodyBytes, 1, pl.length);

        byte properties = 0x10;
        int seq = sequenceManager.next(deviceId);
        byte[] frame = FrameUtil.buildFrame(properties, seq, bodyBytes);

        io.netty.channel.Channel ch = connMgr.getChannel(deviceId);
        if (ch != null && ch.isActive()) {
            ch.writeAndFlush(io.netty.buffer.Unpooled.wrappedBuffer(frame));
            res.put("sent", true);
            res.put("seq", seq);
        } else {
            commandService.queuePending(deviceId, frame);
            res.put("sent", false);
            res.put("queued", true);
            res.put("seq", seq);
        }
        return res;
    }
}
