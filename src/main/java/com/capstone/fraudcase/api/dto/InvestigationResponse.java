package com.capstone.fraudcase.api.dto;

import com.capstone.fraudcase.domain.CaseStatus;

import java.util.List;

/**
 * @param fallbackUsed true when automated analysis failed validation and the safe manual-review outcome was recorded
 * @param policyGrounded true when approved policy passages grounded the recommendation; false on RAG abstention
 */
public record InvestigationResponse(
        String caseId,
        CaseStatus status,
        boolean humanReviewRequired,
        boolean fallbackUsed,
        boolean policyGrounded,
        RecommendationResponse recommendation,
        List<EvidenceItemResponse> evidence) {
}
