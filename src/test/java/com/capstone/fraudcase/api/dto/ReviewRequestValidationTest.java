package com.capstone.fraudcase.api.dto;

import com.capstone.fraudcase.domain.Classification;
import com.capstone.fraudcase.domain.ReviewDecision;
import com.capstone.fraudcase.domain.Route;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewRequestValidationTest {

    private static final String RATIONALE = "Merchant confirmed the order was cancelled before dispatch.";

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private static Set<String> violatedPaths(ReviewRequest request) {
        return validator.validate(request).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    @Test
    void validate_acceptWithRationale_noViolations() {
        assertThat(validator.validate(new ReviewRequest("rec-1", ReviewDecision.ACCEPT, null, null, RATIONALE))).isEmpty();
    }

    @Test
    void validate_overrideWithFinalOutcome_noViolations() {
        ReviewRequest request = new ReviewRequest("rec-1", ReviewDecision.OVERRIDE, Classification.MERCHANT_DISPUTE,
                Route.DISPUTE_OPERATIONS, RATIONALE);

        assertThat(validator.validate(request)).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "short"})
    void validate_overrideWithoutReason_blocked(String rationale) {
        ReviewRequest request = new ReviewRequest("rec-1", ReviewDecision.OVERRIDE, Classification.MERCHANT_DISPUTE,
                Route.DISPUTE_OPERATIONS, rationale);

        assertThat(violatedPaths(request)).containsExactly("rationale");
    }

    @Test
    void validate_overrideWithoutFinalClassificationAndRoute_blocked() {
        ReviewRequest request = new ReviewRequest("rec-1", ReviewDecision.OVERRIDE, null, null, RATIONALE);

        assertThat(violatedPaths(request)).containsExactlyInAnyOrder("finalClassification", "finalRoute");
    }

    @Test
    void validate_acceptWithoutRecommendationId_blocked() {
        assertThat(violatedPaths(new ReviewRequest(null, ReviewDecision.ACCEPT, null, null, RATIONALE)))
                .containsExactly("recommendationId");
    }

    @Test
    void validate_closeWithoutRecommendationId_noViolations() {
        assertThat(validator.validate(new ReviewRequest(null, ReviewDecision.CLOSE, null, null, RATIONALE))).isEmpty();
    }

    @Test
    void validate_missingDecision_blocked() {
        assertThat(violatedPaths(new ReviewRequest("rec-1", null, null, null, RATIONALE))).containsExactly("decision");
    }

    @Test
    void validate_rationaleWithCardNumber_blocked() {
        ReviewRequest request = new ReviewRequest("rec-1", ReviewDecision.ACCEPT, null, null,
                "Customer card 4111111111111111 confirmed stolen");

        assertThat(violatedPaths(request)).containsExactly("rationale");
    }
}
