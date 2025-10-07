package com.ev07b.commands;

import io.netty.channel.Channel;
import com.ev07b.model.EV07BMessage;

public interface CommandProcessor {
    int commandId();
    void handle(EV07BMessage msg, Channel ch);
}
