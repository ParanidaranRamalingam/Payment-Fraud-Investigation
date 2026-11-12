package com.capstone.fraudcase.application;

public class RecommendationNotFoundException extends RuntimeException {

    public RecommendationNotFoundException(String caseId) {
        super("No recommendation recorded for case " + caseId);
    }
}
