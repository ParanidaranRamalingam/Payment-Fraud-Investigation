package com.capstone.fraudcase.security;

import com.capstone.fraudcase.api.dto.ApiErrorResponse;
import com.capstone.fraudcase.observability.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Clock;
import java.util.List;

/** Writes 401/403 as the standard {@link ApiErrorResponse} envelope, without revealing why authentication failed. */
public class ApiSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    static final String REALM = "fraud-capstone";

    private final JsonMapper jsonMapper;
    private final Clock clock;

    public ApiSecurityErrorHandler(JsonMapper jsonMapper, Clock clock) {
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException e)
            throws IOException {
        response.setHeader("WWW-Authenticate", "Basic realm=\"" + REALM + "\"");
        write(request, response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication is required");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException e)
            throws IOException {
        write(request, response, HttpStatus.FORBIDDEN, "FORBIDDEN", "Your role does not permit this operation");
    }

    private void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String code,
                       String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        jsonMapper.writeValue(response.getOutputStream(), ApiErrorResponse.of(status, code, message, List.of(),
                CorrelationIdFilter.current(request), clock.instant()));
    }
}
