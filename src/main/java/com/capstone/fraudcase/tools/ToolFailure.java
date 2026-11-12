package com.capstone.fraudcase.tools;

import com.capstone.fraudcase.domain.EvidenceStatus;
import com.capstone.fraudcase.domain.ToolTraceStatus;

/** Structured tool failure categories and how each is traced and represented as evidence. */
public enum ToolFailure {
    NOT_FOUND(ToolTraceStatus.NOT_FOUND, EvidenceStatus.NOT_FOUND, "NOT_FOUND", "no matching record was found"),
    TIMEOUT(ToolTraceStatus.TIMEOUT, EvidenceStatus.TIMEOUT, "TOOL_TIMEOUT", "the tool timed out"),
    UNAVAILABLE(ToolTraceStatus.ERROR, EvidenceStatus.UNAVAILABLE, "TOOL_UNAVAILABLE", "the source was unavailable"),
    ERROR(ToolTraceStatus.ERROR, EvidenceStatus.UNAVAILABLE, "TOOL_ERROR", "the tool failed unexpectedly"),
    CONTRACT_VIOLATION(ToolTraceStatus.ERROR, EvidenceStatus.UNAVAILABLE, "TOOL_CONTRACT_VIOLATION",
            "the tool returned an invalid result"),
    REJECTED(ToolTraceStatus.REJECTED, EvidenceStatus.UNAVAILABLE, "TOOL_NOT_ALLOWED",
            "the tool is not on the evidence allow-list");

    private final ToolTraceStatus traceStatus;
    private final EvidenceStatus evidenceStatus;
    private final String errorCode;
    private final String description;

    ToolFailure(ToolTraceStatus traceStatus, EvidenceStatus evidenceStatus, String errorCode, String description) {
        this.traceStatus = traceStatus;
        this.evidenceStatus = evidenceStatus;
        this.errorCode = errorCode;
        this.description = description;
    }

    public ToolTraceStatus traceStatus() {
        return traceStatus;
    }

    public EvidenceStatus evidenceStatus() {
        return evidenceStatus;
    }

    public String errorCode() {
        return errorCode;
    }

    public String description() {
        return description;
    }
}
