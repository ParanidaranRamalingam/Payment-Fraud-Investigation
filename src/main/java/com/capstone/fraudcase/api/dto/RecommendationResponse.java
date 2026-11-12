package com.capstone.fraudcase.api.dto;

import com.capstone.fraudcase.domain.Classification;
import com.capstone.fraudcase.domain.FraudCaseRecommendation;
import com.capstone.fraudcase.domain.ProcessorMode;
import com.capstone.fraudcase.domain.RiskLevel;
import com.capstone.fraudcase.domain.Route;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record RecommendationResponse(
        String recommendationId,
        String caseId,
        Classification classification,
        RiskLevel riskLevel,
        Route route,
        BigDecimal confidence,
        boolean humanReviewRequired,
        boolean insufficientEvidence,
        List<String> evidenceIds,
        List<PolicyCitationResponse> policyCitations,
        List<String> missingInformation,
        List<String> traceIds,
        String rationale,
        ProcessorMode processorMode,
        Instant createdAt) {

    public static RecommendationResponse from(String recommendationId, FraudCaseRecommendation rec, Instant createdAt) {
        return new RecommendationResponse(recommendationId, rec.caseId(), rec.classification(), rec.riskLevel(),
                rec.route(), rec.confidence(), rec.humanReviewRequired(),
                rec.classification() == Classification.INSUFFICIENT_EVIDENCE, rec.evidenceIds(),
                rec.policyCitations().stream().map(PolicyCitationResponse::from).toList(),
                rec.missingInformation(), rec.traceIds(), rec.rationale(), rec.processorMode(), createdAt);
    }
}
