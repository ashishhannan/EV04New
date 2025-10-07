package com.ev07b.services;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import com.ev07b.repos.PendingCommandRepository;
import com.ev07b.entities.PendingCommandEntity;
import java.util.List;

@Service
public class CommandService {

    @Autowired
    private PendingCommandRepository pendingRepo;

    public PendingCommandEntity queuePending(String deviceId, byte[] payload) {
        PendingCommandEntity p = new PendingCommandEntity(deviceId, payload);
        return pendingRepo.save(p);
    }

    public List<PendingCommandEntity> listPendingFor(String deviceId) {
        return pendingRepo.findByDeviceId(deviceId);
    }

    public void delete(PendingCommandEntity p) {
        pendingRepo.delete(p);
    }
}
