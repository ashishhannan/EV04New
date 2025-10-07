package com.ev07b.server;

import com.ev07b.codec.EV07BEncoder;
import com.ev07b.codec.EV07BFrameDecoder;
import com.ev07b.handler.EV07BBusinessHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;

/**
 * EV070Server - TCP listener for EV04/EV07 devices.
 * Accepts an EV07BBusinessHandler instance (typically a Spring bean) to install into Netty pipeline.
 */
public class EV070Server {

    private final int port;
    private final EV07BBusinessHandler businessHandler;
    private Channel serverChannel;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    /** Default to 7000 and no handler (not recommended) */
    public EV070Server() {
        this(7000, null);
    }

    /** Create server with port and a business handler instance */
    public EV070Server(int port, EV07BBusinessHandler businessHandler) {
        this.port = port;
        this.businessHandler = businessHandler;
    }

    public void start() throws InterruptedException {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        // Add your decoder/encoder
                        ch.pipeline().addLast(new EV07BFrameDecoder());
                        ch.pipeline().addLast(new EV07BEncoder());

                        // Install the Spring-managed handler, or if none provided, try to create one (not recommended)
                        if (businessHandler != null) {
                            ch.pipeline().addLast(businessHandler);
                        } else {
                            // Fallback — user must ensure handler is safe to construct this way
                            //ch.pipeline().addLast(new com.ev07b.handler.EV07BBusinessHandler(null));
                            System.err.println("[WARN] No business handler injected into EV070Server!");
                        }
                    }
                })
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childOption(ChannelOption.TCP_NODELAY, true);

        ChannelFuture future = bootstrap.bind(port).sync();
        serverChannel = future.channel();
        System.out.println("EV07B TCP Server started on port " + port);
    }

    public void stop() {
        try {
            if (serverChannel != null) {
                serverChannel.close().sync();
            }
        } catch (InterruptedException ignored) {
        } finally {
            if (bossGroup != null) bossGroup.shutdownGracefully();
            if (workerGroup != null) workerGroup.shutdownGracefully();
            System.out.println("EV07B TCP Server stopped.");
        }
    }
}
