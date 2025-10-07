package com.ev07b.repos;

import org.springframework.data.jpa.repository.JpaRepository;
import com.ev07b.entities.CommandLogEntity;

public interface CommandLogRepository extends JpaRepository<CommandLogEntity, Long> {
}
