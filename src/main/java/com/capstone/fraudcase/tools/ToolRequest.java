package com.capstone.fraudcase.tools;

import com.capstone.fraudcase.domain.TransactionClaim;

import java.time.Instant;
import java.util.Objects;

/**
 * Case-scoped input to an evidence tool. Contains only masked/tokenized identifiers and the validated claim.
 * Synthetic tools derive their output from the identifiers, never from {@code caseId}, so identical identifiers
 * yield identical evidence in any case.
 */
public record ToolRequest(
        String caseId,
        String customerReference,
        String cardReference,
        TransactionClaim claim,
        Instant caseCreatedAt,
        Instant asOf) {

    public ToolRequest {
        Objects.requireNonNull(caseId, "caseId");
        Objects.requireNonNull(customerReference, "customerReference");
        Objects.requireNonNull(claim, "claim");
        Objects.requireNonNull(asOf, "asOf");
    }

    /** Safe description for traces: masked card reference and claim identifiers only. */
    public String redactedSummary() {
        return "txn=%s merchant=%s card=%s claimType=%s".formatted(claim.transactionReference(),
                claim.merchantName(), cardReference == null ? "none" : cardReference, claim.claimType());
    }
}
