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
    protected void channelRead0(ChannelHandlerContext ctx, EV07BMessage msg) throws Exception {
        String deviceId = msg.getDeviceId();
        if (deviceId != null && !deviceId.isEmpty()) {
            // Register active channel for this device
            connMgr.register(deviceId, ctx.channel());

            // Update last-seen timestamp
            deviceService.touch(deviceId);

            // Check for pending commands
            List<PendingCommandEntity> pending = pendingRepo.findByDeviceId(deviceId);
            for (PendingCommandEntity p : pending) {
                if (ctx.channel().isActive()) {
                    byte[] payload = p.getPayload();
                    if (payload != null && payload.length > 0) {
                        ctx.channel().writeAndFlush(io.netty.buffer.Unpooled.wrappedBuffer(payload));
                    }
                    pendingRepo.delete(p);
                }
            }
        }

        // Dispatch to the appropriate command handler
        if (dispatcher != null) {
            dispatcher.dispatch(msg, ctx.channel());
        } else {
            System.err.println("[Netty] CommandDispatcher not initialized yet");
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        Channel ch = ctx.channel();
        System.out.println("[Netty] Channel inactive: " + ch.remoteAddress());
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        System.err.println("[Netty] Exception in handler: " + cause.getMessage());
        cause.printStackTrace();
        ctx.close();
    }
}
