package com.capstone.fraudcase.api.dto;

import com.capstone.fraudcase.domain.EvidenceTool;
import com.capstone.fraudcase.domain.ToolTrace;
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

    public static ToolTraceResponse from(ToolTrace t) {
        return new ToolTraceResponse(t.traceId(), t.tool(), t.status(), t.requestSummary(), t.resultSummary(),
                t.errorCode(), t.startedAt(), t.completedAt(), t.durationMs());
    }
}
