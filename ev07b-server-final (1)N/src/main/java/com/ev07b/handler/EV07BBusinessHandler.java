package com.ev07b.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler.Sharable;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.List;

import com.ev07b.model.EV07BMessage;
import com.ev07b.commands.CommandDispatcher;
import com.ev07b.commands.DeviceConnectionManager;
import com.ev07b.services.DeviceService;
import com.ev07b.services.CommandService;
import com.ev07b.repos.PendingCommandRepository;
import com.ev07b.entities.PendingCommandEntity;

/**
 * EV07BBusinessHandler
 *
 * Core business logic for incoming EV07B messages.
 * Handles registration, heartbeats, and pending command delivery.
 *
 * This class is @Sharable because a single Spring bean is reused
 * across multiple Netty channels.
 */
@Sharable
@Component
public class EV07BBusinessHandler extends SimpleChannelInboundHandler<EV07BMessage> {

    private static CommandDispatcher dispatcher;
    private final ApplicationContext ctx;

    private final DeviceService deviceService;
    private final DeviceConnectionManager connMgr;
    private final CommandService commandService;
    private final PendingCommandRepository pendingRepo;

    @Autowired
    public EV07BBusinessHandler(
            ApplicationContext ctx,
            DeviceService deviceService,
            DeviceConnectionManager connMgr,
            CommandService commandService,
            PendingCommandRepository pendingRepo) {

        this.ctx = ctx;
        this.deviceService = deviceService;
        this.connMgr = connMgr;
        this.commandService = commandService;
        this.pendingRepo = pendingRepo;
    }

    @PostConstruct
    public void init() {
        dispatcher = ctx.getBean(CommandDispatcher.class);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx0, EV07BMessage msg) throws Exception {
        Channel ch = ctx0.channel();
        String deviceId = msg.getDeviceId();
        String resolvedId = deviceId;
        boolean msgIdIsUnknown = resolvedId != null && resolvedId.equalsIgnoreCase("UNKNOWN");
        if (resolvedId == null || resolvedId.isEmpty() || msgIdIsUnknown) {
            // Fallback to last known mapping for this channel
            String fromChannel = connMgr.getDeviceId(ch);
            if (fromChannel != null && !fromChannel.isEmpty()) {
                resolvedId = fromChannel;
                System.out.println("[Business] DeviceId resolved from channel " + ch.id().asShortText() + ": " + resolvedId + (msgIdIsUnknown?" (message had UNKNOWN)":""));
            } else {
                System.out.println("[Business] No deviceId in message (or UNKNOWN) and no mapping for channel " + ch.id().asShortText());
            }
        } else {
            System.out.println("[Business] DeviceId from message: " + deviceId);
        }

        if (resolvedId != null && !resolvedId.isEmpty() && !resolvedId.equalsIgnoreCase("UNKNOWN")) {
            // Register active channel for this device (do not register UNKNOWN)
            connMgr.register(resolvedId, ch);

            // Update last-seen timestamp
            deviceService.touch(resolvedId);

            // Check for pending commands
            List<PendingCommandEntity> pending = pendingRepo.findByDeviceId(resolvedId);
            for (PendingCommandEntity p : pending) {
                if (ch.isActive()) {
                    byte[] payload = p.getPayload();
                    if (payload != null && payload.length > 0) {
                        ch.writeAndFlush(io.netty.buffer.Unpooled.wrappedBuffer(payload));
                    }
                    pendingRepo.delete(p);
                }
            }
        }

        // If we resolved a better device id, wrap a new message to pass downstream
        EV07BMessage toDispatch = msg;
        if ((deviceId == null || deviceId.isEmpty() || deviceId.equalsIgnoreCase("UNKNOWN")) && resolvedId != null && !resolvedId.isEmpty()) {
            toDispatch = new EV07BMessage(
                    resolvedId,
                    msg.getCommandId(),
                    msg.getPayload(),
                    msg.getProperties(),
                    msg.getSequenceId());
        }

        if (resolvedId != null && !resolvedId.isEmpty()) {
            System.out.println("[Business] Dispatch cmd=0x" + Integer.toHexString(toDispatch.getCommandId()) + " to device " + resolvedId);
        }

        // Dispatch to the appropriate command handler
        if (dispatcher != null) {
            dispatcher.dispatch(toDispatch, ch);
        } else {
            System.err.println("[Netty] CommandDispatcher not initialized yet");
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx0) throws Exception {
        Channel ch = ctx0.channel();
        System.out.println("[Netty] Channel inactive: " + ch.remoteAddress());
        super.channelInactive(ctx0);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx0, Throwable cause) throws Exception {
        System.err.println("[Netty] Exception in handler: " + cause.getMessage());
        cause.printStackTrace();
        ctx0.close();
    }
}
