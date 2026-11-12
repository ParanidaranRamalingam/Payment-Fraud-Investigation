package com.capstone.fraudcase.agent.springai;

import com.capstone.fraudcase.domain.Classification;
import com.capstone.fraudcase.domain.RiskLevel;
import com.capstone.fraudcase.domain.Route;

import java.math.BigDecimal;
import java.util.List;

/**
 * Structured model output, mapped onto the framework-neutral {@code FraudCaseRecommendation} by the adapter.
 * The model may only choose enum values, cite identifiers that were supplied to it and write bounded text;
 * {@code humanReviewRequired} and {@code processorMode} are never model-controlled and are forced by the adapter.
 */
public record ModelAssessment(
        Classification classification,
        RiskLevel riskLevel,
        Route route,
        BigDecimal confidence,
        List<String> evidenceIds,
        List<String> citedChunkIds,
        List<String> missingInformation,
        List<String> traceIds,
        String rationale) {
}
