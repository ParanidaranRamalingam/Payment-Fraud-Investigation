package com.capstone.fraudcase.observability.health;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SchemaMigrationHealthIndicatorTest {

    @Mock
    private Flyway flyway;

    @Mock
    private MigrationInfoService infoService;

    private SchemaMigrationHealthIndicator indicator;

    @BeforeEach
    void setUp() {
        indicator = new SchemaMigrationHealthIndicator(flyway);
    }

    private void givenMigrations(MigrationInfo current, MigrationInfo[] all, MigrationInfo[] pending) {
        when(flyway.info()).thenReturn(infoService);
        when(infoService.current()).thenReturn(current);
        when(infoService.all()).thenReturn(all);
        when(infoService.pending()).thenReturn(pending);
    }

    private static MigrationInfo migration(String version, MigrationState state) {
        MigrationInfo info = mock(MigrationInfo.class);
        when(info.getState()).thenReturn(state);
        if (version != null) {
            when(info.getVersion()).thenReturn(MigrationVersion.fromVersion(version));
        }
        return info;
    }

    @Test
    void health_allApplied_reportsUpWithCurrentVersion() {
        MigrationInfo v1 = migration("1", MigrationState.SUCCESS);
        givenMigrations(v1, new MigrationInfo[]{v1}, new MigrationInfo[0]);

        Health health = indicator.health(true);

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("currentVersion", "1").containsEntry("pending", 0)
                .containsEntry("failed", 0L);
    }

    @Test
    void health_pendingMigration_reportsDown() {
        MigrationInfo v1 = migration("1", MigrationState.SUCCESS);
        MigrationInfo v2 = migration(null, MigrationState.PENDING);
        givenMigrations(v1, new MigrationInfo[]{v1, v2}, new MigrationInfo[]{v2});

        Health health = indicator.health(true);

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("pending", 1);
    }

    @Test
    void health_failedMigration_reportsDown() {
        MigrationInfo v1 = migration("1", MigrationState.FAILED);
        givenMigrations(v1, new MigrationInfo[]{v1}, new MigrationInfo[0]);

        Health health = indicator.health(true);

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("failed", 1L);
    }

    @Test
    void health_emptySchema_reportsDown() {
        givenMigrations(null, new MigrationInfo[0], new MigrationInfo[0]);

        Health health = indicator.health(true);

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("currentVersion", "none");
    }

    @Test
    void health_flywayThrows_reportsDownWithoutLeakingExceptionMessage() {
        when(flyway.info()).thenThrow(new IllegalStateException("jdbc:mysql://secret-host password=x"));

        Health health = indicator.health(true);

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails().toString()).doesNotContain("secret-host").doesNotContain("password");
    }
}
