package com.ev07b.commands;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import io.netty.channel.Channel;

import com.ev07b.model.EV07BMessage;
import com.ev07b.services.DeviceService;
import com.ev07b.repos.CommandLogRepository;
import com.ev07b.entities.CommandLogEntity;

/**
 * EV04HeartbeatProcessor
 *
 * Handles EV04/EV07B heartbeat with command id 0x03 (as observed from real device).
 * Uses the currently wired simple encoder (EV07BFrameEncoder in com.ev07b.net),
 * which frames outbound byte[] as: [0xAB][len BE][payload][CRC16 BE].
 *
 * ACK payload is minimal: [0x03, 0x01] where 0x01 indicates OK.
 * Adjust if your device requires a different status byte.
 */
@Component
public class EV04HeartbeatProcessor implements CommandProcessor {

    private static final int EV04_HEARTBEAT_CMD = 0x03;

    @Autowired
    private DeviceService deviceService;

    @Autowired
    private CommandLogRepository logRepo;

    @Override
    public int commandId() {
        return EV04_HEARTBEAT_CMD;
    }

    @Override
    public void handle(EV07BMessage msg, Channel ch) {
        String deviceId = msg.getDeviceId();
        byte[] payload = msg.getPayload();

        // Update last seen for device
        deviceService.touch(deviceId);

        // Log inbound heartbeat
        logRepo.save(new CommandLogEntity(deviceId, msg.getCommandId(), payload));

        // Build minimal ACK payload for EV04 heartbeat
        byte[] ackPayload = new byte[] { (byte) EV04_HEARTBEAT_CMD, 0x01 };

        // Write raw payload so EV07BFrameEncoder (simple) frames it
        if (ch != null && ch.isActive()) {
            ch.writeAndFlush(ackPayload);
        }
    }
}

