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
 * @param policyGrounded true when at least one approved policy passage met the similarity threshold;
 *                       when false the retrieval abstained and the recommendation was driven to manual review
 */
public record InvestigationResult(
        String caseId,
        CaseStatus status,
        String recommendationId,
        FraudCaseRecommendation recommendation,
        boolean fallbackUsed,
        boolean policyGrounded,
        List<EvidenceItem> evidence) {

    public InvestigationResult {
        evidence = List.copyOf(evidence);
    }
}
