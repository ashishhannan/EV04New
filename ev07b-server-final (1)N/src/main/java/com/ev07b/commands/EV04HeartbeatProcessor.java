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

/**
 * EV04HeartbeatProcessor
 *
 * Handles Services command (0x03) heartbeats from device. Heartbeat is a key (0x10) inside this command.
 * Responds with ACK (0x7F) if ACK flag requested.
 */
@Component
public class EV04HeartbeatProcessor implements CommandProcessor {

    private static final int SERVICES_CMD = 0x03;

    @Autowired
    private DeviceService deviceService;

    @Autowired
    private CommandLogRepository logRepo;

    @Override
    public int commandId() {
        return SERVICES_CMD;
    }

    @Override
    public void handle(EV07BMessage msg, Channel ch) {
        String deviceId = msg.getDeviceId();
        byte[] payload = msg.getPayload();

        // Update last seen for device
        deviceService.touch(deviceId);

        // Log inbound services message
        logRepo.save(new CommandLogEntity(deviceId, msg.getCommandId(), payload));

        // If ACK requested (properties bit4), respond with Negative Response (0x7F) success code (0x00)
        boolean ackRequested = (msg.getProperties() & 0x10) != 0;
        if (ackRequested && ch != null && ch.isActive()) {
            byte[] ackBody = new byte[] { (byte) 0x7F, 0x01, 0x00 };
            byte properties = 0x00; // don't request ACK for ACK
            int seq = msg.getSequenceId();
            byte[] frame = FrameUtil.buildFrame(properties, seq, ackBody);
            ch.writeAndFlush(Unpooled.wrappedBuffer(frame));
        }
    }
}
