package com.capstone.fraudcase.api.validation;

import com.capstone.fraudcase.api.dto.ReviewRequest;
import com.capstone.fraudcase.domain.ReviewDecision;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ValidReviewRequestValidator implements ConstraintValidator<ValidReviewRequest, ReviewRequest> {

    @Override
    public boolean isValid(ReviewRequest request, ConstraintValidatorContext context) {
        if (request == null || request.decision() == null) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        boolean valid = true;
        if (request.decision() == ReviewDecision.OVERRIDE) {
            valid &= require(request.finalClassification() != null, "finalClassification",
                    "is required when overriding the recommendation", context);
            valid &= require(request.finalRoute() != null, "finalRoute",
                    "is required when overriding the recommendation", context);
        }
        if (request.decision() != ReviewDecision.CLOSE) {
            valid &= require(request.recommendationId() != null && !request.recommendationId().isBlank(),
                    "recommendationId", "is required when accepting or overriding", context);
        }
        return valid;
    }

    private static boolean require(boolean condition, String field, String message, ConstraintValidatorContext context) {
        if (!condition) {
            context.buildConstraintViolationWithTemplate(message).addPropertyNode(field).addConstraintViolation();
        }
        return condition;
    }
}
