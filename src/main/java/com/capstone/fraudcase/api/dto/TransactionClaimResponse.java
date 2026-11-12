package com.capstone.fraudcase.api.dto;

import com.capstone.fraudcase.domain.ClaimType;

import java.math.BigDecimal;
import java.time.Instant;

public record TransactionClaimResponse(
        String transactionReference,
        String merchantName,
        String merchantCategoryCode,
        BigDecimal amount,
        String currency,
        Instant transactionAt,
        ClaimType claimType) {
}
