package com.capstone.fraudcase.persistence;

import org.flywaydb.core.Flyway;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.sql.Connection;
import java.sql.SQLException;

@TestConfiguration(proxyBeanMethods = false)
public class MySqlTestConfiguration {

    @Bean
    FlywayMigrationStrategy cleanThenMigrate() {
        return flyway -> {
            String schema = currentSchema(flyway);
            if (schema == null || !schema.endsWith("_test")) {
                throw new IllegalStateException("Refusing to clean non-test schema: " + schema);
            }
            flyway.clean();
            flyway.migrate();
        };
    }

    private static String currentSchema(Flyway flyway) {
        try (Connection connection = flyway.getConfiguration().getDataSource().getConnection()) {
            return connection.getCatalog();
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot determine test schema", e);
        }
    }
}
