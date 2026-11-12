package com.capstone.fraudcase.application;

import com.capstone.fraudcase.domain.FraudCaseRecommendation;

import java.time.Instant;

/** A persisted, re-validated recommendation with its ID and creation time. */
public record RecordedRecommendation(String recommendationId, FraudCaseRecommendation recommendation,
                                     Instant createdAt) {
}
