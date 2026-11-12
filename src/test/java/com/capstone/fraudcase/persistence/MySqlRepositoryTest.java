package com.capstone.fraudcase.persistence;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Repository/migration test against a real MySQL 8 schema (no H2, no embedded replacement).
 * Requires {@code DB_TEST_URL}, {@code DB_USERNAME} and {@code DB_PASSWORD}; reported as skipped otherwise.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Tag("mysql")
@EnabledIfEnvironmentVariable(named = "DB_TEST_URL", matches = "jdbc:mysql:.+")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(MySqlTestConfiguration.class)
public @interface MySqlRepositoryTest {
}
