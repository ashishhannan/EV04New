package com.ev07b.net;

import org.springframework.stereotype.Component;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class SequenceManager {
    private final ConcurrentHashMap<String, AtomicInteger> counters = new ConcurrentHashMap<>();

    public int next(String deviceId) {
        return counters.computeIfAbsent(deviceId, k -> new AtomicInteger((int)(System.nanoTime() & 0xFFFF)))
                .updateAndGet(prev -> (prev + 1) & 0xFFFF);
    }
}

