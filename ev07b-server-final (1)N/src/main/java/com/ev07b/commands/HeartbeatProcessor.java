package com.ev07b.commands;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import io.netty.channel.Channel;
import io.netty.buffer.Unpooled;

import com.ev07b.model.EV07BMessage;
import com.ev07b.services.DeviceService;
import com.ev07b.repos.CommandLogRepository;
import com.ev07b.entities.CommandLogEntity;
import com.ev07b.net.FrameUtil;
import com.ev07b.services.GeofenceEvaluatorService;

import java.nio.ByteBuffer;

@Component
public class HeartbeatProcessor implements CommandProcessor {

    private static final int HEARTBEAT_CMD = 0x10;
    private static final int KEY_DEVICE_ID = 0x01;
    private static final int KEY_GPS = 0x20;

    @Autowired
    private DeviceService deviceService;

    @Autowired
    private CommandLogRepository logRepo;

    @Autowired
    private GeofenceEvaluatorService geofenceEvaluator;

    @Override
    public int commandId() {
        return HEARTBEAT_CMD;
    }

    @Override
    public void handle(EV07BMessage msg, Channel ch) {
        String deviceId = msg.getDeviceId();
        byte[] payload = msg.getPayload();

        // Update last seen
        deviceService.touch(deviceId);

        // Log inbound heartbeat
        logRepo.save(new CommandLogEntity(deviceId, msg.getCommandId(), payload));

        // Parse optional GPS key from heartbeat body (LE per protocol)
        if (payload != null && payload.length >= 1) {
            try {
                // Body layout: [cmd=0x10][keyLen][key][value...] ...
                int i = 1; // skip command byte
                while (i < payload.length) {
                    int keyLen = Byte.toUnsignedInt(payload[i++]);
                    if (keyLen < 1 || i + keyLen - 1 > payload.length) break;
                    int key = Byte.toUnsignedInt(payload[i++]);
                    int valueLen = keyLen - 1;
                    if (key == KEY_GPS && valueLen == 21) {
                        // Little-endian GPS value per doc
                        ByteBuffer bb = ByteBuffer.wrap(payload, i, valueLen).order(java.nio.ByteOrder.LITTLE_ENDIAN);
                        int lat_i = bb.getInt();
                        int lon_i = bb.getInt();
                        // skip speed(2), direction(2), altitude(2), acc(2), mileage(4)
                        bb.position(bb.position() + 2 + 2 + 2 + 2 + 4);
                        int sats = bb.get() & 0xFF;
                        double lat = lat_i / 1e7; // GPS uses 1e7 per doc
                        double lon = lon_i / 1e7;
                        // Evaluate geofences for transitions and alarms
                        geofenceEvaluator.evaluateAndNotify(deviceId, lat, lon);
                    }
                    i += valueLen;
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        // Build ACK payload per protocol: echo command with status OK (0x01)
        byte[] ackPayload = new byte[] { (byte) HEARTBEAT_CMD, 0x01 };

        // Use same properties and sequence as request (unless spec requires different flags)
        byte properties = msg.getProperties();
        int seq = msg.getSequenceId();

        byte[] frame = FrameUtil.buildFrame(properties, seq, ackPayload);

        if (ch != null && ch.isActive()) {
            ch.writeAndFlush(Unpooled.wrappedBuffer(frame));
        }
    }
}
