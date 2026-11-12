package com.capstone.fraudcase.agent.springai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * {@code fraud.ai.enabled}: when true the Spring AI adapter is the primary {@code FraudCaseProcessor}; when
 * false (default) the deterministic rules processor runs and no model is required.
 * {@code fraud.ai.timeout}: time budget for a single model call before it is abandoned and the manual-review
 * fallback is used.
 */
@ConfigurationProperties("fraud.ai")
public record FraudAiProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("15s") Duration timeout) {

    public FraudAiProperties {
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("fraud.ai.timeout must be positive");
        }
    }
}
