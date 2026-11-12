package com.capstone.fraudcase.tools;

import java.util.Objects;

/** Raised by a tool provider to report an expected, structured failure (not found, timeout, unavailable). */
public class ToolException extends RuntimeException {

    private final ToolFailure failure;

    public ToolException(ToolFailure failure, String message) {
        super(message);
        this.failure = Objects.requireNonNull(failure, "failure");
    }

    public ToolFailure failure() {
        return failure;
    }
}
