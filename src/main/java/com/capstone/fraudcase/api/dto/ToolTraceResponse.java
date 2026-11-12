package com.capstone.fraudcase.api.dto;

import com.capstone.fraudcase.domain.EvidenceTool;
import com.capstone.fraudcase.domain.ToolTraceStatus;

import java.time.Instant;

public record ToolTraceResponse(
        String traceId,
        EvidenceTool tool,
        ToolTraceStatus status,
        String requestSummary,
        String resultSummary,
        String errorCode,
        Instant startedAt,
        Instant completedAt,
        Long durationMs) {
}
