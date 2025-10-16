package com.ev07b.commands;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import io.netty.channel.Channel;

@Component
public class DeviceConnectionManager {
    private final Map<String, Channel> sessions = new ConcurrentHashMap<>();
    private final Map<String, String> channelToDevice = new ConcurrentHashMap<>();

    public void register(String deviceId, Channel ch) {
        sessions.put(deviceId, ch);
        if (ch != null) {
            channelToDevice.put(ch.id().asLongText(), deviceId);
        }
        System.out.println("[DeviceConnectionManager] Registered device " + deviceId);
    }

    public void unregister(String deviceId) {
        sessions.remove(deviceId);
        System.out.println("[DeviceConnectionManager] Unregistered device " + deviceId);
    }

    public Channel getChannel(String deviceId) {
        return sessions.get(deviceId);
    }

    public boolean isConnected(String deviceId) {
        Channel ch = sessions.get(deviceId);
        return ch != null && ch.isActive();
    }

    public String getDeviceId(Channel ch) {
        if (ch == null) return null;
        return channelToDevice.get(ch.id().asLongText());
    }
}
