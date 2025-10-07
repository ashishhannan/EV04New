package com.ev07b;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import com.ev07b.server.EV070Server;
import com.ev07b.handler.EV07BBusinessHandler;

@Component
public class NettyServerStarter {
    private EV070Server server;

    @Autowired
    private EV07BBusinessHandler businessHandler;

    @PostConstruct
    public void start() throws Exception {
        // Pass the Spring-managed handler into the server so Netty uses the bean instance
        server = new EV070Server(7000, businessHandler);
        new Thread(() -> {
            try {
                server.start();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, "ev07b-netty").start();
    }

    @PreDestroy
    public void stop() throws Exception {
        if (server != null) {
            server.stop();
        }
    }
}

