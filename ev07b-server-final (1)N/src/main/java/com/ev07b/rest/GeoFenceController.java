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
    private static final byte CMD_CONFIGURATION = (byte) 0x02; // per spec: configuration command
    private static final byte KEY_GEOFENCE = (byte) 0x51;      // geofence key under configuration

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
        // Accept simple circle geofence: radius (meters), center lat/lon in decimal degrees optional
        int radius = body.getOrDefault("radius", 100) instanceof Number ? ((Number) body.get("radius")).intValue() : parseIntOr(body.get("radius"), 100);
        double latD = body.getOrDefault("lat", 0.0) instanceof Number ? ((Number) body.get("lat")).doubleValue() : parseDoubleOr(body.get("lat"), 0.0);
        double lonD = body.getOrDefault("lon", 0.0) instanceof Number ? ((Number) body.get("lon")).doubleValue() : parseDoubleOr(body.get("lon"), 0.0);

        // Build flags LE per spec:
        // Bit0-3: index (0)
        // Bit4-7: points (0)
        // Bit8: enable=1
        // Bit9: direction (0=Out default)
        // Bit10: type (0=circle)
        // Bit16-31: radius meters
        int flags = (1 << 8) | ((radius & 0xFFFF) << 16);

        int lat_i = (int) Math.round(latD * 10_000_000); // use 1e7 like GPS key
        int lon_i = (int) Math.round(lonD * 10_000_000);

        // Store only key-value payload (flags LE + lat LE + lon LE)
        byte[] keyValue = new byte[4 + 4 + 4];
        // flags LE
        keyValue[0] = (byte) (flags & 0xFF);
        keyValue[1] = (byte) ((flags >>> 8) & 0xFF);
        keyValue[2] = (byte) ((flags >>> 16) & 0xFF);
        keyValue[3] = (byte) ((flags >>> 24) & 0xFF);
        // lat LE
        keyValue[4] = (byte) (lat_i & 0xFF);
        keyValue[5] = (byte) ((lat_i >>> 8) & 0xFF);
        keyValue[6] = (byte) ((lat_i >>> 16) & 0xFF);
        keyValue[7] = (byte) ((lat_i >>> 24) & 0xFF);
        // lon LE
        keyValue[8]  = (byte) (lon_i & 0xFF);
        keyValue[9]  = (byte) ((lon_i >>> 8) & 0xFF);
        keyValue[10] = (byte) ((lon_i >>> 16) & 0xFF);
        keyValue[11] = (byte) ((lon_i >>> 24) & 0xFF);

        GeofenceEntity entity = new GeofenceEntity(deviceId, "geofence-" + System.currentTimeMillis(), keyValue);
        GeofenceEntity saved = geofenceRepo.save(entity);

        // Build message body: [cmd=0x02][keyLen][key=0x51][keyValue]
        int keyLen = 1 + keyValue.length; // includes key byte
        byte[] bodyBytes = new byte[1 + 1 + keyLen];
        int i = 0;
        bodyBytes[i++] = CMD_CONFIGURATION;
        bodyBytes[i++] = (byte) keyLen;
        bodyBytes[i++] = KEY_GEOFENCE;
        System.arraycopy(keyValue, 0, bodyBytes, i, keyValue.length);

        byte properties = 0x10; // request ACK
        int seq = sequenceManager.next(deviceId);
        byte[] frame = FrameUtil.buildFrame(properties, seq, bodyBytes);

        io.netty.channel.Channel ch = connMgr.getChannel(deviceId);
        if (ch != null && ch.isActive()) {
            log.info("Sending geofence (cfg+key=0x51) to device {} seq={}", deviceId, seq);
            ch.writeAndFlush(io.netty.buffer.Unpooled.wrappedBuffer(frame));
        } else {
            log.info("Device offline; queueing geofence for {} seq={}", deviceId, seq);
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

        // Use stored keyValue to build proper message body [cmd][keyLen][key][value]
        byte[] kv = g.getPayload();
        int keyLen = 1 + (kv == null ? 0 : kv.length);
        byte[] bodyBytes = new byte[1 + 1 + keyLen];
        int i = 0;
        bodyBytes[i++] = CMD_CONFIGURATION;
        bodyBytes[i++] = (byte) keyLen;
        bodyBytes[i++] = KEY_GEOFENCE;
        if (kv != null && kv.length > 0) System.arraycopy(kv, 0, bodyBytes, i, kv.length);

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

    private static int parseIntOr(Object v, int def) {
        try {
            if (v instanceof String) return Integer.parseInt((String) v);
        } catch (Exception ignore) {}
        return def;
    }
    private static double parseDoubleOr(Object v, double def) {
        try {
            if (v instanceof String) return Double.parseDouble((String) v);
        } catch (Exception ignore) {}
        return def;
    }
}
