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

        // Parse optional GPS key from heartbeat body (LE per protocol). Some devices may not include GPS here.
        if (payload != null && payload.length >= 1) {
            try {
                // Body layout: [cmd=0x10][keyLen][key][value...] ...
                int i = 1; // skip command byte
                while (i < payload.length) {
                    int keyLen = Byte.toUnsignedInt(payload[i++]);
                    if (keyLen < 1 || i + keyLen - 1 > payload.length) break;
                    int key = Byte.toUnsignedInt(payload[i++]);
                    int valueLen = keyLen - 1;
                    if (key == KEY_GPS && valueLen >= 8) {
                        // Little-endian GPS value per doc (lat/lon in 1e7)
                        ByteBuffer bb = ByteBuffer.wrap(payload, i, valueLen).order(java.nio.ByteOrder.LITTLE_ENDIAN);
                        int lat_i = bb.getInt();
                        int lon_i = bb.getInt();
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

        // If ACK requested (properties bit4), reply with ACK frame per spec: cmd 0x7F, keyLen=0x01, key=0x00 (success)
        boolean ackRequested = (msg.getProperties() & 0x10) != 0;
        if (ackRequested && ch != null && ch.isActive()) {
            byte[] ackBody = new byte[] { (byte) 0x7F, 0x01, 0x00 };
            byte properties = 0x00; // do not request ACK for ACK
            int seq = msg.getSequenceId(); // echo sequence id
            byte[] frame = FrameUtil.buildFrame(properties, seq, ackBody);
            ch.writeAndFlush(Unpooled.wrappedBuffer(frame));
        }
    }
}
