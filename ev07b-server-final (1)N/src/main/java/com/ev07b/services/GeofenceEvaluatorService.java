package com.ev07b.services;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import com.ev07b.repos.GeofenceRepository;
import com.ev07b.entities.GeofenceEntity;
import com.ev07b.commands.DeviceConnectionManager;
import com.ev07b.net.SequenceManager;
import com.ev07b.net.FrameUtil;

import io.netty.channel.Channel;
import io.netty.buffer.Unpooled;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GeofenceEvaluatorService {

    private static final byte CMD_GEOFENCE = (byte)0x51;

    private final GeofenceRepository geofenceRepo;
    private final DeviceConnectionManager connMgr;
    private final SequenceManager sequenceManager;

    // Track last inside/outside state per device+geofenceId
    private final Map<String, Map<Long, Boolean>> lastState = new ConcurrentHashMap<>();

    @Autowired
    public GeofenceEvaluatorService(GeofenceRepository geofenceRepo,
                                    DeviceConnectionManager connMgr,
                                    SequenceManager sequenceManager) {
        this.geofenceRepo = geofenceRepo;
        this.connMgr = connMgr;
        this.sequenceManager = sequenceManager;
    }

    public void evaluateAndNotify(String deviceId, double lat, double lon) {
        List<GeofenceEntity> fences = geofenceRepo.findByDeviceId(deviceId);
        if (fences == null || fences.isEmpty()) return;

        Map<Long, Boolean> deviceMap = lastState.computeIfAbsent(deviceId, k -> new ConcurrentHashMap<>());

        for (GeofenceEntity g : fences) {
            ParsedFence pf = parseFence(g.getPayload());
            if (pf == null) continue;
            if (!pf.enable) {
                System.out.println("[GeofenceEvaluator] Fence idx=" + pf.index + " disabled; skip");
                continue;
            }
            if (pf.type != 0) {
                System.out.println("[GeofenceEvaluator] Fence idx=" + pf.index + " type=polygon not supported yet; skip");
                continue; // only circle for now
            }

            double distM = haversineMeters(lat, lon, pf.centerLat, pf.centerLon);
            boolean inside = distM <= (pf.radius <= 0 ? 0.0 : pf.radius);
            Boolean prev = deviceMap.put(g.getId(), inside);

            System.out.println(String.format(
                "[GeofenceEvaluator] device=%s fenceId=%d idx=%d dir=%s radius=%dm center=(%.6f,%.6f) cur=(%.6f,%.6f) dist=%.1fm prev=%s now=%s",
                deviceId, g.getId(), pf.index, (pf.direction==0?"OUT":"IN"), pf.radius,
                pf.centerLat, pf.centerLon, lat, lon, distM,
                (prev==null?"null":(prev?"inside":"outside")), (inside?"inside":"outside")));

            if (prev != null && prev.booleanValue() != inside) {
                boolean leaving = prev && !inside;
                boolean entering = !prev && inside;
                boolean shouldTrigger = (pf.direction == 0 && leaving) || (pf.direction == 1 && entering);
                System.out.println("[GeofenceEvaluator] Transition " + (prev?"inside->outside":"outside->inside") + ", shouldTrigger=" + shouldTrigger);
                if (shouldTrigger) {
                    sendAlarm(deviceId, pf, lat, lon, distM);
                }
            }
        }
    }

    private static class ParsedFence {
        int index; int points; boolean enable; int direction; int type; int radius; double centerLat; double centerLon;
    }

    // Accept either full command payload [0x51][flags][lat][lon]... or key-value only [flags][lat][lon]
    private ParsedFence parseFence(byte[] payload) {
        if (payload == null) return null;
        ByteBuffer bb = ByteBuffer.wrap(payload).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        int start = 0;
        if (payload.length >= 1 && Byte.toUnsignedInt(payload[0]) == (CMD_GEOFENCE & 0xFF)) {
            // skip command byte
            if (payload.length < 1 + 4 + 8) return null;
            bb.position(1);
        } else {
            if (payload.length < 4 + 8) return null;
        }
        int flags = bb.getInt();
        ParsedFence pf = new ParsedFence();
        pf.index = flags & 0x0F;
        pf.points = (flags >>> 4) & 0x0F;
        pf.enable = ((flags >>> 8) & 0x01) == 1;
        pf.direction = (flags >>> 9) & 0x01;
        pf.type = (flags >>> 10) & 0x01;
        pf.radius = (flags >>> 16) & 0xFFFF;
        int lat_i = bb.getInt();
        int lon_i = bb.getInt();
        pf.centerLat = lat_i / 1e7; // align with controller storage
        pf.centerLon = lon_i / 1e7;
        return pf;
    }

    // Simple Haversine distance in meters
    private double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat/2)*Math.sin(dLat/2) + Math.cos(Math.toRadians(lat1))*Math.cos(Math.toRadians(lat2))*Math.sin(dLon/2)*Math.sin(dLon/2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        return R * c;
    }

    // Send a minimal, framed geofence-alarm notification back to device (proprietary but documented here)
    // Payload: [0x51][0xA1][index][state][radiusLE(2)][latLE(4)][lonLE(4)]
    //   state: 0x00=in, 0x01=out
    private void sendAlarm(String deviceId, ParsedFence pf, double curLat, double curLon, double distM) {
        try {
            byte state = (byte) ((distM <= pf.radius) ? 0x00 : 0x01);
            int lat_i = (int)Math.round(curLat * 10_000_000);
            int lon_i = (int)Math.round(curLon * 10_000_000);
            byte[] payload = new byte[1 + 1 + 1 + 1 + 2 + 4 + 4];
            int i = 0;
            payload[i++] = CMD_GEOFENCE;
            payload[i++] = (byte)0xA1;         // alarm subcode
            payload[i++] = (byte)(pf.index & 0xFF);
            payload[i++] = state;
            // radius LE
            payload[i++] = (byte)(pf.radius & 0xFF);
            payload[i++] = (byte)((pf.radius >>> 8) & 0xFF);
            // lat LE
            payload[i++] = (byte)(lat_i & 0xFF);
            payload[i++] = (byte)((lat_i >>> 8) & 0xFF);
            payload[i++] = (byte)((lat_i >>> 16) & 0xFF);
            payload[i++] = (byte)((lat_i >>> 24) & 0xFF);
            // lon LE
            payload[i++] = (byte)(lon_i & 0xFF);
            payload[i++] = (byte)((lon_i >>> 8) & 0xFF);
            payload[i++] = (byte)((lon_i >>> 16) & 0xFF);
            payload[i++] = (byte)((lon_i >>> 24) & 0xFF);

            byte props = 0x10; // request ACK
            int seq = sequenceManager.next(deviceId);
            byte[] frame = FrameUtil.buildFrame(props, seq, payload);

            Channel ch = connMgr.getChannel(deviceId);
            if (ch != null && ch.isActive()) {
                ch.writeAndFlush(Unpooled.wrappedBuffer(frame));
                System.out.println("[GeofenceEvaluator] Alarm sent to " + deviceId + " idx=" + pf.index + " state=" + (state==1?"OUT":"IN") + " radius=" + pf.radius + "m dist=" + Math.round(distM) + "m");
            } else {
                System.out.println("[GeofenceEvaluator] Device offline, alarm not sent: " + deviceId);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
