package com.capstone.fraudcase.application;

import com.capstone.fraudcase.domain.CaseStatus;
import com.capstone.fraudcase.domain.EvidenceItem;
import com.capstone.fraudcase.domain.FraudCaseRecommendation;

import java.util.List;

/**
 * Outcome of one investigation run.
 *
 * @param fallbackUsed true when the processor failed or its output was rejected and the safe manual-review
 *                     recommendation was recorded instead
 */
public record InvestigationResult(
        String caseId,
        CaseStatus status,
        String recommendationId,
        FraudCaseRecommendation recommendation,
        boolean fallbackUsed,
        List<EvidenceItem> evidence) {

    public InvestigationResult {
        evidence = List.copyOf(evidence);
    }
}
