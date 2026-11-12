package com.capstone.fraudcase.application;

import com.capstone.fraudcase.domain.CaseStatus;
import com.capstone.fraudcase.domain.EvidenceItem;

import java.util.List;

/** Result of {@code POST /investigations}: the resulting case status, the recorded recommendation and evidence. */
public record InvestigationOutcome(
        String caseId,
        CaseStatus status,
        boolean fallbackUsed,
        RecordedRecommendation recommendation,
        List<EvidenceItem> evidence) {

    public InvestigationOutcome {
        evidence = List.copyOf(evidence);
    }
}
