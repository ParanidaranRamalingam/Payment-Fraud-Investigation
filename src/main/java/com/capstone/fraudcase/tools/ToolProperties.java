package com.capstone.fraudcase.tools;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.regex.Pattern;

/**
 * {@code fraud.tools.timeout}: per-invocation time budget for each evidence tool.
 * {@code fraud.tools.fixture-seed}: global seed mixed into every synthetic fixture value (recorded in provenance).
 */
@ConfigurationProperties("fraud.tools")
public record ToolProperties(
        @DefaultValue("2500ms") Duration timeout,
        @DefaultValue(ToolProperties.DEFAULT_FIXTURE_SEED) String fixtureSeed) {

    public static final String DEFAULT_FIXTURE_SEED = "fraud-training-v1";
    private static final Pattern SEED = Pattern.compile("^[a-z0-9-]{1,32}$");

    public ToolProperties {
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("fraud.tools.timeout must be positive");
        }
        if (fixtureSeed == null || !SEED.matcher(fixtureSeed).matches()) {
            throw new IllegalArgumentException("fraud.tools.fixture-seed must be 1-32 chars of [a-z0-9-]");
        }
    }

    /** Properties with the default fixture seed (records keep a single constructor for configuration binding). */
    public static ToolProperties withTimeout(Duration timeout) {
        return new ToolProperties(timeout, DEFAULT_FIXTURE_SEED);
    }
}
