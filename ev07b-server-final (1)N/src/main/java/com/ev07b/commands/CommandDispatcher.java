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
        }
    }
}
