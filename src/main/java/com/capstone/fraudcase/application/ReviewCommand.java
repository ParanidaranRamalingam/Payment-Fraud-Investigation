package com.capstone.fraudcase.application;

import com.capstone.fraudcase.domain.Classification;
import com.capstone.fraudcase.domain.ReviewDecision;
import com.capstone.fraudcase.domain.Route;

/** A reviewer's submitted disposition; the reviewer ID comes from the authenticated principal. */
public record ReviewCommand(String recommendationId, ReviewDecision decision, Classification finalClassification,
                            Route finalRoute, String rationale) {
}
