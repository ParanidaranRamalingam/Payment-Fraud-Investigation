package com.capstone.fraudcase.agent.common;

import com.capstone.fraudcase.TestFixtures;
import com.capstone.fraudcase.agent.rules.RulesFraudCaseAgent;
import com.capstone.fraudcase.domain.ClaimType;
import com.capstone.fraudcase.domain.Classification;
import com.capstone.fraudcase.domain.FraudCaseRecommendation;
import com.capstone.fraudcase.domain.InvalidRecommendationException;
import com.capstone.fraudcase.domain.ProcessorMode;
import com.capstone.fraudcase.domain.RiskLevel;
import com.capstone.fraudcase.domain.Route;
import com.capstone.fraudcase.support.SyntheticCases;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecommendationValidatorTest {

    private final RecommendationValidator validator = new RecommendationValidator();
    private final FraudCaseContext context = SyntheticCases.context("txn_fraud_001", ClaimType.UNRECOGNISED_TRANSACTION);

    private FraudCaseRecommendation recommendation(String caseId, List<String> evidence, List<String> traces,
                                                   String rationale, List<String> missing) {
        return new FraudCaseRecommendation(caseId, Classification.UNAUTHORISED_TRANSACTION, RiskLevel.HIGH,
                Route.FRAUD_INVESTIGATION, new BigDecimal("0.800"), true, evidence, List.of(), missing, traces,
                rationale, ProcessorMode.RULES);
    }

    private FraudCaseRecommendation valid() {
        return recommendation(context.caseId(), context.evidenceIds(), List.of("rule:R-X", "trace-1"), "Reason.",
                List.of());
    }

    @Test
    void validate_rulesOutput_accepted() {
        FraudCaseRecommendation rec = new RulesFraudCaseAgent().analyze(context);

        assertThat(validator.validate(rec, context)).isSameAs(rec);
    }

    @Test
    void validate_validRecommendation_returnedUnchanged() {
        FraudCaseRecommendation rec = valid();

        assertThat(validator.validate(rec, context)).isSameAs(rec);
    }

    @Test
    void t09_null_rejected() {
        assertThatThrownBy(() -> validator.validate(null, context)).isInstanceOf(InvalidRecommendationException.class);
    }

    @Test
    void t09_otherCaseId_rejected() {
        assertThatThrownBy(() -> validator.validate(
                recommendation("other-case", context.evidenceIds(), List.of(), "Reason.", List.of()), context))
                .isInstanceOf(InvalidRecommendationException.class).hasMessageContaining("different case");
    }

    @Test
    void t09_evidenceOutsideCase_rejected() {
        assertThatThrownBy(() -> validator.validate(
                recommendation(context.caseId(), List.of("ev-1", "ev-from-case-b"), List.of(), "Reason.", List.of()),
                context)).isInstanceOf(InvalidRecommendationException.class).hasMessageContaining("outside this case");
    }

    @Test
    void t09_duplicateEvidence_rejected() {
        assertThatThrownBy(() -> validator.validate(
                recommendation(context.caseId(), List.of("ev-1", "ev-1"), List.of(), "Reason.", List.of()), context))
                .isInstanceOf(InvalidRecommendationException.class);
    }

    @Test
    void t09_unknownTrace_rejected() {
        assertThatThrownBy(() -> validator.validate(
                recommendation(context.caseId(), context.evidenceIds(), List.of("made-up-trace"), "Reason.", List.of()),
                context)).isInstanceOf(InvalidRecommendationException.class).hasMessageContaining("unknown trace");
    }

    @Test
    void t09_uncitedPolicyPassage_rejected() {
        FraudCaseRecommendation rec = new FraudCaseRecommendation(context.caseId(),
                Classification.UNAUTHORISED_TRANSACTION, RiskLevel.HIGH, Route.FRAUD_INVESTIGATION,
                new BigDecimal("0.800"), true, context.evidenceIds(), List.of(TestFixtures.policyCitation()), List.of(),
                List.of(), "Reason.", ProcessorMode.RULES);

        assertThatThrownBy(() -> validator.validate(rec, context))
                .isInstanceOf(InvalidRecommendationException.class).hasMessageContaining("policy");
    }

    @Test
    void t09_rationaleWithUnmaskedCardNumber_rejected() {
        assertThatThrownBy(() -> validator.validate(recommendation(context.caseId(), context.evidenceIds(), List.of(),
                "Card 4111 1111 1111 1111 was used.", List.of()), context))
                .isInstanceOf(InvalidRecommendationException.class);
    }

    @Test
    void t09_overlongMissingInformation_rejected() {
        assertThatThrownBy(() -> validator.validate(recommendation(context.caseId(), context.evidenceIds(), List.of(),
                "Reason.", List.of("x".repeat(501))), context)).isInstanceOf(InvalidRecommendationException.class);
    }

    @Test
    void fallback_isValidForContextAndRoutesToManualReview() {
        FraudCaseContext gaps = SyntheticCases.context("txn_unavail_001", ClaimType.UNRECOGNISED_TRANSACTION);

        FraudCaseRecommendation fallback = FallbackRecommendations.manualReview(gaps, "processor failure");

        assertThat(validator.validate(fallback, gaps)).isSameAs(fallback);
        assertThat(fallback.route()).isEqualTo(Route.MANUAL_REVIEW);
        assertThat(fallback.classification()).isEqualTo(Classification.INSUFFICIENT_EVIDENCE);
        assertThat(fallback.humanReviewRequired()).isTrue();
        assertThat(fallback.confidence()).isEqualByComparingTo("0");
        assertThat(fallback.missingInformation()).contains("Automated analysis unavailable: processor failure",
                "getDeviceSignals returned no evidence (UNAVAILABLE)");
    }
}
