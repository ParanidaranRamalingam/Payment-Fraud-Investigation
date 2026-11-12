package com.capstone.fraudcase.api.dto;

import com.capstone.fraudcase.domain.EvidenceItem;
import com.capstone.fraudcase.domain.EvidenceStatus;
import com.capstone.fraudcase.domain.EvidenceTool;
import com.capstone.fraudcase.domain.ProvenanceType;

import java.time.Instant;

public record EvidenceItemResponse(
        String evidenceId,
        EvidenceTool tool,
        ProvenanceType provenance,
        EvidenceStatus status,
        String summary,
        String toolTraceId,
        Instant collectedAt) {

    public static EvidenceItemResponse from(EvidenceItem item) {
        return new EvidenceItemResponse(item.evidenceId(), item.tool(), item.provenance(), item.status(),
                item.summary(), item.toolTraceId(), item.collectedAt());
    }
}
