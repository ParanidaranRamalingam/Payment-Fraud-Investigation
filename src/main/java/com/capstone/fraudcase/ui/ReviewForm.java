package com.capstone.fraudcase.ui;

import com.capstone.fraudcase.api.validation.SafeText;
import com.capstone.fraudcase.domain.Classification;
import com.capstone.fraudcase.domain.ReviewDecision;
import com.capstone.fraudcase.domain.ReviewDisposition;
import com.capstone.fraudcase.domain.Route;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Human-review form. The reviewer identity is always the authenticated principal, never the form. */
public class ReviewForm {

    private String recommendationId;

    @NotNull(message = "a disposition is required")
    private ReviewDecision decision;

    private Classification finalClassification;

    private Route finalRoute;

    @NotBlank(message = "a reviewer rationale is mandatory")
    @Size(min = ReviewDisposition.MIN_RATIONALE_LENGTH, max = ReviewDisposition.MAX_RATIONALE_LENGTH,
            message = "rationale must be between 10 and 2000 characters")
    @SafeText
    private String rationale;

    private Boolean missingInfoAcknowledged = false;

    public String getRecommendationId() {
        return recommendationId;
    }

    public void setRecommendationId(String recommendationId) {
        this.recommendationId = recommendationId;
    }

    public ReviewDecision getDecision() {
        return decision;
    }

    public void setDecision(ReviewDecision decision) {
        this.decision = decision;
    }

    public Classification getFinalClassification() {
        return finalClassification;
    }

    public void setFinalClassification(Classification finalClassification) {
        this.finalClassification = finalClassification;
    }

    public Route getFinalRoute() {
        return finalRoute;
    }

    public void setFinalRoute(Route finalRoute) {
        this.finalRoute = finalRoute;
    }

    public String getRationale() {
        return rationale;
    }

    public void setRationale(String rationale) {
        this.rationale = rationale;
    }

    public Boolean getMissingInfoAcknowledged() {
        return missingInfoAcknowledged;
    }

    public void setMissingInfoAcknowledged(Boolean missingInfoAcknowledged) {
        this.missingInfoAcknowledged = missingInfoAcknowledged;
    }
}
