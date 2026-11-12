package com.capstone.fraudcase.api.dto;

import com.capstone.fraudcase.domain.CaseStatus;
import com.capstone.fraudcase.domain.Classification;
import com.capstone.fraudcase.domain.ReviewDecision;
import com.capstone.fraudcase.domain.Route;

import java.time.Instant;

public record ReviewResponse(
        String reviewId,
        String caseId,
        String recommendationId,
        String reviewerId,
        ReviewDecision decision,
        Classification finalClassification,
        Route finalRoute,
        String rationale,
        CaseStatus resultingStatus,
        Instant reviewedAt) {
}
