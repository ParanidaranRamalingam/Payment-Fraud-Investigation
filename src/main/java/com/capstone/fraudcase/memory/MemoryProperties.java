package com.capstone.fraudcase.memory;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code fraud.memory.window}: maximum number of most-recent case messages the assistant may see when
 * answering. The window is deliberately small and always scoped to a single case.
 */
@ConfigurationProperties("fraud.memory")
public record MemoryProperties(@DefaultValue("20") int window) {

    public static final int MAX_WINDOW = 100;

    public MemoryProperties {
        if (window < 1 || window > MAX_WINDOW) {
            throw new IllegalArgumentException("fraud.memory.window must be between 1 and " + MAX_WINDOW);
        }
    }
}
