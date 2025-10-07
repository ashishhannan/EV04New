package com.ev07b.repos;

import org.springframework.data.jpa.repository.JpaRepository;
import com.ev07b.entities.PendingCommandEntity;
import java.util.List;

public interface PendingCommandRepository extends JpaRepository<PendingCommandEntity, Long> {
    List<PendingCommandEntity> findByDeviceId(String deviceId);
}
