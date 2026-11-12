package com.capstone.fraudcase.tools.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

final class Attributes {

    private Attributes() {
    }

    static Map<String, String> of(Object... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("attributes need key/value pairs");
        }
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], Objects.toString(keyValues[i + 1], "none"));
        }
        return Collections.unmodifiableMap(map);
    }

    static <T> T require(T value, String field) {
        return Objects.requireNonNull(value, field + " is required");
    }

    static int range(int value, int min, int max, String field) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(field + " must be between " + min + " and " + max);
        }
        return value;
    }
}
