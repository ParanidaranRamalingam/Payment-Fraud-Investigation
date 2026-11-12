package com.capstone.fraudcase.observability.health;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.springframework.boot.health.contributor.AbstractHealthIndicator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Reports DOWN when Flyway has failed or pending migrations, i.e. the MySQL schema does not match what this
 * build expects. Exposed as {@code schemaMigrations} and included in the readiness group.
 */
@Component("schemaMigrations")
public class SchemaMigrationHealthIndicator extends AbstractHealthIndicator {

    private final Flyway flyway;

    public SchemaMigrationHealthIndicator(Flyway flyway) {
        super("Schema migration health check failed");
        this.flyway = flyway;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        MigrationInfoService info;
        try {
            info = flyway.info();
        } catch (RuntimeException e) {
            builder.down().withDetail("error", "schema migration status unavailable");
            return;
        }
        MigrationInfo current = info.current();
        long failed = Arrays.stream(info.all()).filter(m -> m.getState().isFailed()).count();
        int pending = info.pending().length;
        builder.status(failed == 0 && pending == 0 && current != null ? "UP" : "DOWN")
                .withDetail("currentVersion", current == null ? "none" : current.getVersion().getVersion())
                .withDetail("pending", pending)
                .withDetail("failed", failed);
    }
}
