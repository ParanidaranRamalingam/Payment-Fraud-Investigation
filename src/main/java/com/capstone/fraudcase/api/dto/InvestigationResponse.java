package com.capstone.fraudcase.api.dto;

import com.capstone.fraudcase.domain.CaseStatus;

import java.util.List;

/**
 * @param fallbackUsed true when automated analysis failed validation and the safe manual-review outcome was recorded
 */
public record InvestigationResponse(
        String caseId,
        CaseStatus status,
        boolean humanReviewRequired,
        boolean fallbackUsed,
        RecommendationResponse recommendation,
        List<EvidenceItemResponse> evidence) {
}
