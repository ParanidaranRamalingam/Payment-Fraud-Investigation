package com.capstone.fraudcase.domain;

/**
 * What the customer reports at intake. This is the claim, not the investigation outcome
 * ({@link Classification}).
 */
public enum ClaimType {
    UNRECOGNISED_TRANSACTION,
    MERCHANT_ISSUE,
    DUPLICATE_CHARGE,
    OTHER
}
