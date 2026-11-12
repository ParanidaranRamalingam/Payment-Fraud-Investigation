package com.capstone.fraudcase.domain;

import java.math.BigDecimal;
import java.util.List;

import static com.capstone.fraudcase.domain.DomainChecks.required;

/**
 * Typed, advisory-only output of a {@code FraudCaseProcessor}. Construction enforces the recommendation
 * contract, so a malformed recommendation can never be persisted or displayed.
 */
public record FraudCaseRecommendation(
        String caseId,
        Classification classification,
        RiskLevel riskLevel,
        Route route,
        BigDecimal confidence,
        boolean humanReviewRequired,
        List<String> evidenceIds,
        List<PolicyCitation> policyCitations,
        List<String> missingInformation,
        List<String> traceIds,
        String rationale,
        ProcessorMode processorMode) {

    public static final int MAX_RATIONALE_LENGTH = 2000;

    public FraudCaseRecommendation {
        required(caseId, "caseId", InvalidRecommendationException::new);
        required(classification, "classification", InvalidRecommendationException::new);
        required(riskLevel, "riskLevel", InvalidRecommendationException::new);
        required(route, "route", InvalidRecommendationException::new);
        required(confidence, "confidence", InvalidRecommendationException::new);
        required(rationale, "rationale", InvalidRecommendationException::new);
        required(processorMode, "processorMode", InvalidRecommendationException::new);
        if (!humanReviewRequired) {
            throw new InvalidRecommendationException("humanReviewRequired must be true");
        }
        if (confidence.compareTo(BigDecimal.ZERO) < 0 || confidence.compareTo(BigDecimal.ONE) > 0) {
            throw new InvalidRecommendationException("confidence must be between 0 and 1");
        }
        if (rationale.length() > MAX_RATIONALE_LENGTH) {
            throw new InvalidRecommendationException("rationale must be at most " + MAX_RATIONALE_LENGTH + " characters");
        }
        evidenceIds = copyOf(evidenceIds, "evidenceIds");
        policyCitations = copyOf(policyCitations, "policyCitations");
        missingInformation = copyOf(missingInformation, "missingInformation");
        traceIds = copyOf(traceIds, "traceIds");
        if (evidenceIds.isEmpty() && classification != Classification.INSUFFICIENT_EVIDENCE) {
            throw new InvalidRecommendationException(
                    "recommendation must cite evidence or explicitly classify as INSUFFICIENT_EVIDENCE");
        }
        if (classification == Classification.INSUFFICIENT_EVIDENCE && missingInformation.isEmpty()) {
            throw new InvalidRecommendationException("INSUFFICIENT_EVIDENCE must state the missing information");
        }
    }

    private static <T> List<T> copyOf(List<T> values, String field) {
        if (values == null) {
            return List.of();
        }
        if (values.stream().anyMatch(v -> v == null || (v instanceof String s && s.isBlank()))) {
            throw new InvalidRecommendationException(field + " must not contain blank entries");
        }
        return List.copyOf(values);
    }
}
