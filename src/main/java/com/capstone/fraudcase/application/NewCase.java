package com.capstone.fraudcase.application;

import com.capstone.fraudcase.domain.TransactionClaim;

/** Validated intake data. The case ID and analyst ID are never part of it: the server assigns both. */
public record NewCase(String customerReference, String cardReference, TransactionClaim claim, String customerStatement) {
}
