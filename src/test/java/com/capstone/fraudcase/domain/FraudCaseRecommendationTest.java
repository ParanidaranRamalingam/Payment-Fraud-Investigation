package com.capstone.fraudcase.domain;

import com.capstone.fraudcase.TestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FraudCaseRecommendationTest {

    private static FraudCaseRecommendation build(Classification classification, BigDecimal confidence,
                                                 boolean humanReview, List<String> evidenceIds,
                                                 List<String> missing, String rationale) {
        return new FraudCaseRecommendation("case-1", classification, RiskLevel.MEDIUM, Route.MANUAL_REVIEW,
                confidence, humanReview, evidenceIds, List.of(), missing, List.of(), rationale, ProcessorMode.RULES);
    }

    @Test
    void constructor_validRecommendation_keepsHumanReviewAndEvidence() {
        FraudCaseRecommendation rec = TestFixtures.recommendation("case-1", List.of("ev-1", "ev-2"));

        assertThat(rec.humanReviewRequired()).isTrue();
        assertThat(rec.evidenceIds()).containsExactly("ev-1", "ev-2");
        assertThat(rec.policyCitations()).hasSize(1);
    }

    @Test
    void constructor_humanReviewNotRequired_rejectedAsInvalid() {
        assertThatThrownBy(() -> build(Classification.MERCHANT_DISPUTE, new BigDecimal("0.5"), false,
                List.of("ev-1"), List.of(), "rationale"))
                .isInstanceOf(InvalidRecommendationException.class)
                .hasMessageContaining("humanReviewRequired");
    }

    @ParameterizedTest
    @ValueSource(strings = {"-0.001", "1.001", "2", "-1"})
    void constructor_confidenceOutsideZeroToOne_rejectedAsInvalid(String confidence) {
        assertThatThrownBy(() -> build(Classification.MERCHANT_DISPUTE, new BigDecimal(confidence), true,
                List.of("ev-1"), List.of(), "rationale"))
                .isInstanceOf(InvalidRecommendationException.class)
                .hasMessageContaining("confidence");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.000", "1", "1.000", "0.5"})
    void constructor_confidenceBoundaries_accepted(String confidence) {
        assertThat(build(Classification.MERCHANT_DISPUTE, new BigDecimal(confidence), true, List.of("ev-1"),
                List.of(), "rationale").confidence()).isEqualByComparingTo(confidence);
    }

    @Test
    void constructor_noEvidenceAndNotInsufficient_rejectedAsInvalid() {
        assertThatThrownBy(() -> build(Classification.UNAUTHORISED_TRANSACTION, BigDecimal.ONE, true, List.of(),
                List.of(), "rationale"))
                .isInstanceOf(InvalidRecommendationException.class)
                .hasMessageContaining("INSUFFICIENT_EVIDENCE");
    }

    @Test
    void constructor_insufficientEvidenceWithMissingInfo_acceptedWithoutEvidence() {
        FraudCaseRecommendation rec = build(Classification.INSUFFICIENT_EVIDENCE, new BigDecimal("0.2"), true,
                null, List.of("transaction details unavailable"), "Not enough evidence to classify.");

        assertThat(rec.evidenceIds()).isEmpty();
        assertThat(rec.missingInformation()).containsExactly("transaction details unavailable");
    }

    @Test
    void constructor_insufficientEvidenceWithoutMissingInfo_rejectedAsInvalid() {
        assertThatThrownBy(() -> build(Classification.INSUFFICIENT_EVIDENCE, new BigDecimal("0.2"), true,
                List.of(), List.of(), "rationale"))
                .isInstanceOf(InvalidRecommendationException.class)
                .hasMessageContaining("missing information");
    }

    @Test
    void constructor_blankEvidenceEntry_rejectedAsInvalid() {
        assertThatThrownBy(() -> build(Classification.MERCHANT_DISPUTE, BigDecimal.ONE, true,
                Arrays.asList("ev-1", " "), List.of(), "rationale"))
                .isInstanceOf(InvalidRecommendationException.class)
                .hasMessageContaining("evidenceIds");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void constructor_blankRationale_rejectedAsInvalid(String rationale) {
        assertThatThrownBy(() -> build(Classification.MERCHANT_DISPUTE, BigDecimal.ONE, true, List.of("ev-1"),
                List.of(), rationale))
                .isInstanceOf(InvalidRecommendationException.class)
                .hasMessageContaining("rationale");
    }

    @Test
    void constructor_overlongRationale_rejectedAsInvalid() {
        String rationale = "x".repeat(FraudCaseRecommendation.MAX_RATIONALE_LENGTH + 1);

        assertThatThrownBy(() -> build(Classification.MERCHANT_DISPUTE, BigDecimal.ONE, true, List.of("ev-1"),
                List.of(), rationale))
                .isInstanceOf(InvalidRecommendationException.class);
    }

    @Test
    void constructor_missingClassification_rejectedAsInvalid() {
        assertThatThrownBy(() -> build(null, BigDecimal.ONE, true, List.of("ev-1"), List.of(), "rationale"))
                .isInstanceOf(InvalidRecommendationException.class)
                .hasMessageContaining("classification");
    }

    @Test
    void evidenceIds_callerMutatesSourceList_recommendationUnchanged() {
        List<String> source = new ArrayList<>(List.of("ev-1"));
        FraudCaseRecommendation rec = build(Classification.MERCHANT_DISPUTE, BigDecimal.ONE, true, source,
                List.of(), "rationale");

        source.add("ev-injected");

        assertThat(rec.evidenceIds()).containsExactly("ev-1");
        assertThatThrownBy(() -> rec.evidenceIds().add("x")).isInstanceOf(UnsupportedOperationException.class);
    }
}
