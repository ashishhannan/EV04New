package com.ev07b.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import com.ev07b.repos.GeofenceRepository;
import com.ev07b.entities.GeofenceEntity;
import com.ev07b.commands.DeviceConnectionManager;
import com.ev07b.services.CommandService;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api/geofences")
public class GeoFenceController {

    private static final Logger log = LoggerFactory.getLogger(GeoFenceController.class);
    @Autowired
    private GeofenceRepository geofenceRepo;

    @Autowired
    private DeviceConnectionManager connMgr;

    @Autowired
    private CommandService commandService;

    @GetMapping("/{deviceId}")
    public List<GeofenceEntity> listForDevice(@PathVariable String deviceId) {
        return geofenceRepo.findByDeviceId(deviceId);
    }

    @PostMapping("/{deviceId}")
    public GeofenceEntity create(@PathVariable String deviceId, @RequestBody Map<String,Object> body) {
        String name = (String) body.getOrDefault("name", "geofence-" + System.currentTimeMillis());
        String b64 = (String) body.get("payloadBase64");
        byte[] payload = new byte[0];
        if (b64 != null) payload = java.util.Base64.getDecoder().decode(b64);

        GeofenceEntity g = new GeofenceEntity(deviceId, name, payload);
        GeofenceEntity saved = geofenceRepo.save(g);

        io.netty.channel.Channel ch = connMgr.getChannel(deviceId);
        if (ch != null && ch.isActive()) {
            ch.writeAndFlush(io.netty.buffer.Unpooled.wrappedBuffer(payload));
        } else {
            commandService.queuePending(deviceId, payload);
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

        io.netty.channel.Channel ch = connMgr.getChannel(deviceId);
        if (ch != null && ch.isActive()) {
            ch.writeAndFlush(io.netty.buffer.Unpooled.wrappedBuffer(g.getPayload()));
            res.put("sent", true);
        } else {
            commandService.queuePending(deviceId, g.getPayload());
            res.put("sent", false);
            res.put("queued", true);
        }
        return res;
    }
}
