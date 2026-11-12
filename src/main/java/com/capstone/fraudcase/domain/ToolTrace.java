package com.capstone.fraudcase.domain;

import java.time.Instant;

/** Read-only view of one evidence-tool invocation, as recorded for provenance. */
public record ToolTrace(
        String traceId,
        String caseId,
        EvidenceTool tool,
        ToolTraceStatus status,
        String requestSummary,
        String resultSummary,
        String errorCode,
        String correlationId,
        Instant startedAt,
        Instant completedAt,
        Long durationMs) {
}
