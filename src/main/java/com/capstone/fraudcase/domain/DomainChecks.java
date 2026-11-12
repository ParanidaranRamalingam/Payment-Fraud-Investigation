package com.capstone.fraudcase.domain;

import java.util.function.Function;

final class DomainChecks {

    private DomainChecks() {
    }

    static <T> T required(T value, String field, Function<String, ? extends DomainValidationException> error) {
        if (value == null || (value instanceof String s && s.isBlank())) {
            throw error.apply(field + " is required");
        }
        return value;
    }

    static <T> T required(T value, String field) {
        return required(value, field, DomainValidationException::new);
    }

    static String maxLength(String value, int max, String field) {
        if (value != null && value.length() > max) {
            throw new DomainValidationException(field + " must be at most " + max + " characters");
        }
        return value;
    }
}
