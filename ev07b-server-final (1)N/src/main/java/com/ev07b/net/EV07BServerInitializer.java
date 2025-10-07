package com.ev07b.net;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import com.ev07b.handler.EV07BBusinessHandler;

public class EV07BServerInitializer extends ChannelInitializer<SocketChannel> {

    private final EV07BBusinessHandler businessHandler;

    public EV07BServerInitializer(EV07BBusinessHandler businessHandler) {
        this.businessHandler = businessHandler;
    }

    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ch.pipeline().addLast(new IdleStateHandler(0,0,60));
        ch.pipeline().addLast(new EV07BFrameDecoder());
        ch.pipeline().addLast(new EV07BFrameEncoder());
        ch.pipeline().addLast(businessHandler);
    }
}
