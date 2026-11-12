package com.capstone.fraudcase.api.dto;

import com.capstone.fraudcase.api.validation.SafeText;
import com.capstone.fraudcase.api.validation.ValidReviewRequest;
import com.capstone.fraudcase.domain.Classification;
import com.capstone.fraudcase.domain.ReviewDisposition;
import com.capstone.fraudcase.domain.ReviewDecision;
import com.capstone.fraudcase.domain.Route;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@ValidReviewRequest
public record ReviewRequest(
        String recommendationId,

        @NotNull
        ReviewDecision decision,

        Classification finalClassification,

        Route finalRoute,

        @NotBlank
        @Size(min = ReviewDisposition.MIN_RATIONALE_LENGTH, max = ReviewDisposition.MAX_RATIONALE_LENGTH)
        @SafeText
        String rationale) {
}
