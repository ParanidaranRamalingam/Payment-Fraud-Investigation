package com.capstone.fraudcase.api.dto;

import java.time.Instant;
import java.util.List;

/** Error envelope. Never carries stack traces, SQL or rejected sensitive values. */
public record ApiErrorResponse(
        int status,
        String error,
        String message,
        List<FieldError> fieldErrors,
        String correlationId,
        Instant timestamp) {

    public record FieldError(String field, String message) {
    }
}
