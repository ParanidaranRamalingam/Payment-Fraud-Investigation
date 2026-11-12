package com.capstone.fraudcase.api.dto;

import com.capstone.fraudcase.domain.CaseStatus;

import java.time.Instant;
import java.util.Set;

public record CaseResponse(
        String caseId,
        CaseStatus status,
        Set<CaseStatus> allowedNextStatuses,
        String customerReference,
        String cardReference,
        TransactionClaimResponse transaction,
        String customerStatement,
        String createdBy,
        boolean humanReviewRequired,
        Instant createdAt,
        Instant updatedAt) {
}
