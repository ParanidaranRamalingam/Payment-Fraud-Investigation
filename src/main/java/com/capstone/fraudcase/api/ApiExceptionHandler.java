package com.capstone.fraudcase.api;

import com.capstone.fraudcase.api.dto.ApiErrorResponse;
import com.capstone.fraudcase.application.CaseConflictException;
import com.capstone.fraudcase.application.CaseNotFoundException;
import com.capstone.fraudcase.application.RecommendationNotFoundException;
import com.capstone.fraudcase.domain.DomainValidationException;
import com.capstone.fraudcase.domain.InvalidCaseTransitionException;
import com.capstone.fraudcase.observability.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindingResult;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Maps failures to the {@link ApiErrorResponse} envelope with a stable error code. Messages are fixed or
 * system-authored; rejected input values, SQL and stack traces are never returned.
 *
 * <pre>
 * 400 VALIDATION_FAILED / MALFORMED_REQUEST     bean validation or unreadable body
 * 404 CASE_NOT_FOUND / RECOMMENDATION_NOT_FOUND
 * 409 INVALID_TRANSITION                        state machine forbids the change
 * 409 CASE_CONFLICT / CONCURRENT_MODIFICATION   closed case, stale recommendation, optimistic-lock loss
 * 422 BUSINESS_RULE_VIOLATION                   well-formed request that breaks a domain rule
 * </pre>
 */
@RestControllerAdvice(basePackageClasses = ApiExceptionHandler.class)
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private final Clock clock;

    public ApiExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> invalid(MethodArgumentNotValidException e, HttpServletRequest request) {
        BindingResult result = e.getBindingResult();
        List<ApiErrorResponse.FieldError> fields = Stream.concat(
                        result.getFieldErrors().stream()
                                .map(f -> new ApiErrorResponse.FieldError(f.getField(), f.getDefaultMessage())),
                        result.getGlobalErrors().stream()
                                .map(g -> new ApiErrorResponse.FieldError(g.getObjectName(), g.getDefaultMessage())))
                .sorted(Comparator.comparing(ApiErrorResponse.FieldError::field)
                        .thenComparing(ApiErrorResponse.FieldError::message))
                .toList();
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", fields, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiErrorResponse> unreadable(HttpMessageNotReadableException e, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST",
                "Request body is missing, malformed or contains unknown or invalid fields", List.of(), request);
    }

    @ExceptionHandler(CaseNotFoundException.class)
    ResponseEntity<ApiErrorResponse> caseNotFound(CaseNotFoundException e, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "CASE_NOT_FOUND", "Case not found", List.of(), request);
    }

    @ExceptionHandler(RecommendationNotFoundException.class)
    ResponseEntity<ApiErrorResponse> noRecommendation(RecommendationNotFoundException e, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "RECOMMENDATION_NOT_FOUND",
                "No recommendation has been recorded for this case; start an investigation first", List.of(), request);
    }

    @ExceptionHandler(InvalidCaseTransitionException.class)
    ResponseEntity<ApiErrorResponse> invalidTransition(InvalidCaseTransitionException e, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "INVALID_TRANSITION", e.getMessage(), List.of(), request);
    }

    @ExceptionHandler(CaseConflictException.class)
    ResponseEntity<ApiErrorResponse> conflict(CaseConflictException e, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "CASE_CONFLICT", e.getMessage(), List.of(), request);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ApiErrorResponse> concurrent(OptimisticLockingFailureException e, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION",
                "The case was modified by another request; reload it and retry", List.of(), request);
    }

    @ExceptionHandler(DomainValidationException.class)
    ResponseEntity<ApiErrorResponse> domainRule(DomainValidationException e, HttpServletRequest request) {
        return error(HttpStatus.UNPROCESSABLE_CONTENT, "BUSINESS_RULE_VIOLATION", e.getMessage(), List.of(), request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> other(Exception e, HttpServletRequest request) {
        if (e instanceof ErrorResponse framework) {
            HttpStatusCode status = framework.getStatusCode();
            HttpStatus known = HttpStatus.resolve(status.value());
            return error(status, known == null ? "HTTP_" + status.value() : known.name(),
                    known == null ? "Request failed" : known.getReasonPhrase(), List.of(), request);
        }
        log.error("Unhandled {} [correlationId={}]", e.getClass().getName(), CorrelationIdFilter.current(request));
        log.debug("Unhandled exception detail", e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred", List.of(),
                request);
    }

    private ResponseEntity<ApiErrorResponse> error(HttpStatusCode status, String code, String message,
                                                   List<ApiErrorResponse.FieldError> fields, HttpServletRequest request) {
        return ResponseEntity.status(status).body(ApiErrorResponse.of(status, code, message, fields,
                CorrelationIdFilter.current(request), clock.instant()));
    }
}
