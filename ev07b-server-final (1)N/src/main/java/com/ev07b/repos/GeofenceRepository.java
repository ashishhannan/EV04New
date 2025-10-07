package com.ev07b.repos;

import org.springframework.data.jpa.repository.JpaRepository;
import com.ev07b.entities.GeofenceEntity;
import java.util.List;

public interface GeofenceRepository extends JpaRepository<GeofenceEntity, Long> {
    List<GeofenceEntity> findByDeviceId(String deviceId);
}
