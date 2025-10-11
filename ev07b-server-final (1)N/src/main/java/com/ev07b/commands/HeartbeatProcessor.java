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

@Component
public class HeartbeatProcessor implements CommandProcessor {

    private static final int HEARTBEAT_CMD = 0x10;

    @Autowired
    private DeviceService deviceService;

    @Autowired
    private CommandLogRepository logRepo;

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

