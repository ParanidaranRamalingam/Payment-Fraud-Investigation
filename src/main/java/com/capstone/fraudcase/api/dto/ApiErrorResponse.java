package com.capstone.fraudcase.api.dto;

import org.springframework.http.HttpStatusCode;

import java.time.Instant;
import java.util.List;

/**
 * Error envelope. Never carries stack traces, SQL or rejected sensitive values.
 *
 * @param error stable machine-readable code, e.g. {@code VALIDATION_FAILED}, {@code INVALID_TRANSITION}
 */
public record ApiErrorResponse(
        int status,
        String error,
        String message,
        List<FieldError> fieldErrors,
        String correlationId,
        Instant timestamp) {

    public ApiErrorResponse {
        fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
    }

    public static ApiErrorResponse of(HttpStatusCode status, String error, String message,
                                      List<FieldError> fieldErrors, String correlationId, Instant timestamp) {
        return new ApiErrorResponse(status.value(), error, message, fieldErrors, correlationId, timestamp);
    }

    public record FieldError(String field, String message) {
    }
}
