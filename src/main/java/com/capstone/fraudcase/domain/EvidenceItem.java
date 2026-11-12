package com.capstone.fraudcase.domain;

import java.time.Instant;

import static com.capstone.fraudcase.domain.DomainChecks.maxLength;
import static com.capstone.fraudcase.domain.DomainChecks.required;

/**
 * A single material fact with provenance. Unavailable/timeout evidence is represented explicitly,
 * never fabricated.
 */
public record EvidenceItem(
        String evidenceId,
        String caseId,
        EvidenceTool tool,
        ProvenanceType provenance,
        EvidenceStatus status,
        String summary,
        String toolTraceId,
        Instant collectedAt) {

    public static final int MAX_SUMMARY_LENGTH = 2000;

    public EvidenceItem {
        required(evidenceId, "evidenceId");
        required(caseId, "caseId");
        required(provenance, "provenance");
        required(status, "status");
        required(summary, "summary");
        required(collectedAt, "collectedAt");
        maxLength(summary, MAX_SUMMARY_LENGTH, "summary");
        if (provenance == ProvenanceType.TOOL_RESULT && (tool == null || toolTraceId == null || toolTraceId.isBlank())) {
            throw new DomainValidationException("tool evidence must reference its tool and tool trace");
        }
    }

    public boolean isAvailable() {
        return status == EvidenceStatus.AVAILABLE;
    }
}
