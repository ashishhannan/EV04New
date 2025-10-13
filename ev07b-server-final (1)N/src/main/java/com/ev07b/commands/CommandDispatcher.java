package com.ev07b.commands;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import io.netty.channel.Channel;
import com.ev07b.model.EV07BMessage;

@Component
public class CommandDispatcher {
    private final Map<Integer, CommandProcessor> registry = new ConcurrentHashMap<>();

    @Autowired
    public CommandDispatcher(List<CommandProcessor> processors) {
        processors.forEach(p -> registry.put(p.commandId(), p));
    }

    public void dispatch(EV07BMessage msg, Channel ch) {
        CommandProcessor p = registry.get(msg.getCommandId());
        if (p != null) {
            p.handle(msg, ch);
        } else {
            System.out.println("[Dispatcher] Unknown command: " + msg.getCommandId());
            // Additional diagnostics: print first up to 32 bytes of payload
            byte[] pl = msg.getPayload();
            if (pl != null && pl.length > 0) {
                int n = Math.min(pl.length, 32);
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < n; i++) {
                    sb.append(String.format("%02x", pl[i] & 0xff));
                    if (i + 1 < n) sb.append(' ');
                }
                System.out.println("[Dispatcher] Payload[0.." + (n - 1) + "]: " + sb);
            }
        }
    }
}
