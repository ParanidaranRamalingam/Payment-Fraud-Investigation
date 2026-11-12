package com.capstone.fraudcase.agent.common;

import com.capstone.fraudcase.domain.Classification;
import com.capstone.fraudcase.domain.FraudCaseRecommendation;
import com.capstone.fraudcase.domain.ProcessorMode;
import com.capstone.fraudcase.domain.RiskLevel;
import com.capstone.fraudcase.domain.Route;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Safe outcome used when a processor fails or its output is rejected: manual review, nothing inferred. */
public final class FallbackRecommendations {

    public static final String FALLBACK_RULE = RecommendationValidator.RULE_TRACE_PREFIX + "R-SAFE-FALLBACK";

    private FallbackRecommendations() {
    }

    public static FraudCaseRecommendation manualReview(FraudCaseContext context, String reason) {
        List<String> missing = new ArrayList<>();
        missing.add("Automated analysis unavailable: " + reason);
        missing.addAll(context.evidenceGaps());
        return new FraudCaseRecommendation(context.caseId(), Classification.INSUFFICIENT_EVIDENCE, RiskLevel.MEDIUM,
                Route.MANUAL_REVIEW, new BigDecimal("0.000"), true, context.evidenceIds(), List.of(), missing,
                List.of(FALLBACK_RULE),
                "Automated analysis did not produce a valid recommendation (" + reason
                        + "). The case is routed to manual review; no classification was inferred.",
                ProcessorMode.RULES);
    }
}
