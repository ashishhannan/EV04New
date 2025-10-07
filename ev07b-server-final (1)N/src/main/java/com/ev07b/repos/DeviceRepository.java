package com.ev07b.repos;

import org.springframework.data.jpa.repository.JpaRepository;
import com.ev07b.entities.DeviceEntity;

public interface DeviceRepository extends JpaRepository<DeviceEntity, String> {
}
