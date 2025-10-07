package com.ev07b.services;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import com.ev07b.repos.DeviceRepository;
import com.ev07b.entities.DeviceEntity;
import java.time.Instant;
import java.util.Optional;

@Service
public class DeviceService {

    @Autowired
    private DeviceRepository deviceRepo;

    public DeviceEntity touch(String deviceId) {
        Optional<DeviceEntity> opt = deviceRepo.findById(deviceId);
        DeviceEntity d;
        if (opt.isPresent()) {
            d = opt.get();
            d.setLastSeen(Instant.now());
            d.setConnected(true);
        } else {
            d = new DeviceEntity(deviceId);
            d.setLastSeen(Instant.now());
            d.setConnected(true);
        }
        deviceRepo.save(d);
        return d;
    }

    public void markDisconnected(String deviceId) {
        deviceRepo.findById(deviceId).ifPresent(d -> {
            d.setConnected(false);
            deviceRepo.save(d);
        });
    }
}
