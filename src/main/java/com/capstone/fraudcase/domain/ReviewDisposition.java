package com.capstone.fraudcase.domain;

import java.time.Instant;

import static com.capstone.fraudcase.domain.DomainChecks.maxLength;
import static com.capstone.fraudcase.domain.DomainChecks.required;

/**
 * The human reviewer's decision. A rationale is always mandatory; an override must state the final
 * classification and route the reviewer chose instead.
 */
public record ReviewDisposition(
        String caseId,
        String recommendationId,
        String reviewerId,
        ReviewDecision decision,
        Classification finalClassification,
        Route finalRoute,
        String rationale,
        Instant reviewedAt) {

    public static final int MIN_RATIONALE_LENGTH = 10;
    public static final int MAX_RATIONALE_LENGTH = 2000;

    public ReviewDisposition {
        required(caseId, "caseId");
        required(reviewerId, "reviewerId");
        required(decision, "decision");
        required(rationale, "rationale");
        required(reviewedAt, "reviewedAt");
        if (rationale.strip().length() < MIN_RATIONALE_LENGTH) {
            throw new DomainValidationException("rationale must be at least " + MIN_RATIONALE_LENGTH + " characters");
        }
        maxLength(rationale, MAX_RATIONALE_LENGTH, "rationale");
        if (decision == ReviewDecision.OVERRIDE && (finalClassification == null || finalRoute == null)) {
            throw new DomainValidationException("override requires finalClassification and finalRoute");
        }
        if (decision != ReviewDecision.CLOSE && recommendationId == null) {
            throw new DomainValidationException(decision + " requires the reviewed recommendationId");
        }
    }

    public CaseStatus resultingStatus() {
        return decision == ReviewDecision.CLOSE ? CaseStatus.CLOSED : CaseStatus.ROUTED;
    }
}
