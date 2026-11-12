package com.capstone.fraudcase.domain;

import java.time.Instant;

import static com.capstone.fraudcase.domain.DomainChecks.maxLength;
import static com.capstone.fraudcase.domain.DomainChecks.required;

/**
 * A single material fact with provenance. Unavailable/timeout evidence is represented explicitly,
 * never fabricated.
 *
 * <p>Provenance: {@code provenance} says what kind of source produced the fact; tool evidence also carries the
 * {@code tool}, the {@code toolTraceId} of the invocation, the versioned {@code source} that served it and, when
 * available, the canonical typed {@code payload} plus its SHA-256 {@code contentHash}. Identical synthetic inputs
 * therefore produce an identical {@code contentHash}.
 */
public record EvidenceItem(
        String evidenceId,
        String caseId,
        EvidenceTool tool,
        ProvenanceType provenance,
        EvidenceStatus status,
        String summary,
        String toolTraceId,
        Instant collectedAt,
        String source,
        String contentHash,
        String payload) {

    public static final int MAX_SUMMARY_LENGTH = 2000;
    public static final int MAX_SOURCE_LENGTH = 64;
    public static final int MAX_PAYLOAD_LENGTH = 8000;

    public EvidenceItem {
        required(evidenceId, "evidenceId");
        required(caseId, "caseId");
        required(provenance, "provenance");
        required(status, "status");
        required(summary, "summary");
        required(collectedAt, "collectedAt");
        maxLength(summary, MAX_SUMMARY_LENGTH, "summary");
        maxLength(source, MAX_SOURCE_LENGTH, "source");
        maxLength(payload, MAX_PAYLOAD_LENGTH, "payload");
        if (provenance == ProvenanceType.TOOL_RESULT && (tool == null || toolTraceId == null || toolTraceId.isBlank())) {
            throw new DomainValidationException("tool evidence must reference its tool and tool trace");
        }
        if ((contentHash == null) != (payload == null)) {
            throw new DomainValidationException("payload and contentHash must be provided together");
        }
        if (contentHash != null && status != EvidenceStatus.AVAILABLE) {
            throw new DomainValidationException("only AVAILABLE evidence may carry a payload");
        }
        if (contentHash != null && !ContentHash.isSha256Hex(contentHash)) {
            throw new DomainValidationException("contentHash must be a lowercase SHA-256 hex digest");
        }
        if (contentHash != null && !contentHash.equals(ContentHash.sha256Hex(tool + "\n" + payload))) {
            throw new DomainValidationException("contentHash does not match payload");
        }
    }

    public EvidenceItem(String evidenceId, String caseId, EvidenceTool tool, ProvenanceType provenance,
                        EvidenceStatus status, String summary, String toolTraceId, Instant collectedAt) {
        this(evidenceId, caseId, tool, provenance, status, summary, toolTraceId, collectedAt, null, null, null);
    }

    public boolean isAvailable() {
        return status == EvidenceStatus.AVAILABLE;
    }
}
