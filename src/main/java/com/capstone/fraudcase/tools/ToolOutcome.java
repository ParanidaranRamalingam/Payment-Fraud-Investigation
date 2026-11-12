package com.capstone.fraudcase.tools;

import com.capstone.fraudcase.domain.EvidenceStatus;
import com.capstone.fraudcase.domain.EvidenceTool;
import com.capstone.fraudcase.domain.ToolTraceStatus;
import com.capstone.fraudcase.tools.model.EvidencePayload;

import java.time.Instant;
import java.util.Objects;

/**
 * Structured result of one tool invocation. A payload is present if and only if the call succeeded; failures
 * carry an error code and a summary stating that no data was inferred.
 */
public record ToolOutcome(
        EvidenceTool tool,
        String source,
        ToolTraceStatus traceStatus,
        EvidenceStatus evidenceStatus,
        EvidencePayload payload,
        String errorCode,
        String summary,
        String requestSummary,
        Instant startedAt,
        Instant completedAt) {

    public ToolOutcome {
        Objects.requireNonNull(tool, "tool");
        Objects.requireNonNull(traceStatus, "traceStatus");
        Objects.requireNonNull(evidenceStatus, "evidenceStatus");
        Objects.requireNonNull(summary, "summary");
        Objects.requireNonNull(startedAt, "startedAt");
        Objects.requireNonNull(completedAt, "completedAt");
        boolean success = traceStatus == ToolTraceStatus.SUCCESS;
        if (success != (payload != null) || success != (evidenceStatus == EvidenceStatus.AVAILABLE)) {
            throw new IllegalArgumentException("payload must be present exactly when the tool succeeded");
        }
        if (success == (errorCode != null)) {
            throw new IllegalArgumentException("errorCode must be present exactly when the tool failed");
        }
    }

    public boolean isAvailable() {
        return payload != null;
    }
}
