package com.capstone.fraudcase.application;

import com.capstone.fraudcase.domain.CaseStatus;
import com.capstone.fraudcase.domain.TransactionClaim;

import java.time.Instant;

/** Read model of one case. {@code claim} is null only for cases created without a transaction claim. */
public record CaseView(
        String caseId,
        CaseStatus status,
        String customerReference,
        String cardReference,
        TransactionClaim claim,
        String customerStatement,
        String createdBy,
        boolean humanReviewRequired,
        Instant createdAt,
        Instant updatedAt) {
}
