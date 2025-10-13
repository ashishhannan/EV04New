package com.ev07b.config;

import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "app.flyway", name = "repair", havingValue = "true")
public class FlywayRepairConfiguration {

    @Bean
    public FlywayMigrationStrategy repairThenMigrate() {
        return new FlywayMigrationStrategy() {
            @Override
            public void migrate(Flyway flyway) {
                // First fix checksums and failed migrations metadata
                flyway.repair();
                // Then proceed with normal migration
                flyway.migrate();
            }
        };
    }
}

