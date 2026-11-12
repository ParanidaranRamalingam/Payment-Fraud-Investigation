package com.capstone.fraudcase.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;

import static com.capstone.fraudcase.domain.DomainChecks.required;

public record TransactionClaim(
        String transactionReference,
        String merchantName,
        String merchantCategoryCode,
        BigDecimal amount,
        Currency currency,
        Instant transactionAt,
        ClaimType claimType) {

    public TransactionClaim {
        required(transactionReference, "transactionReference");
        required(merchantName, "merchantName");
        required(amount, "amount");
        required(currency, "currency");
        required(transactionAt, "transactionAt");
        required(claimType, "claimType");
        if (amount.signum() <= 0) {
            throw new DomainValidationException("amount must be positive");
        }
        int fractionDigits = currency.getDefaultFractionDigits();
        if (fractionDigits >= 0 && amount.stripTrailingZeros().scale() > fractionDigits) {
            throw new DomainValidationException("amount has more decimal places than " + currency.getCurrencyCode() + " allows");
        }
    }
}
