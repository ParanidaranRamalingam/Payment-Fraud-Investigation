package com.capstone.fraudcase.domain;

public class InvalidRecommendationException extends DomainValidationException {

    public InvalidRecommendationException(String message) {
        super(message);
    }
}
