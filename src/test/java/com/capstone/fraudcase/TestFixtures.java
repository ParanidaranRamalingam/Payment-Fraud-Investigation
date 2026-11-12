package com.capstone.fraudcase;

import com.capstone.fraudcase.api.dto.CreateCaseRequest;
import com.capstone.fraudcase.api.dto.TransactionClaimRequest;
import com.capstone.fraudcase.domain.ClaimType;
import com.capstone.fraudcase.domain.Classification;
import com.capstone.fraudcase.domain.FraudCaseRecommendation;
import com.capstone.fraudcase.domain.PolicyCitation;
import com.capstone.fraudcase.domain.ProcessorMode;
import com.capstone.fraudcase.domain.RiskLevel;
import com.capstone.fraudcase.domain.Route;
import com.capstone.fraudcase.domain.TransactionClaim;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Currency;
import java.util.List;

public final class TestFixtures {

    public static final Instant TRANSACTION_AT = Instant.parse("2026-10-01T09:15:30.123456Z");

    private TestFixtures() {
    }

    public static TransactionClaimRequest validTransactionRequest() {
        return new TransactionClaimRequest("txn_000123", "Synthetic Electronics Ltd", "5732",
                new BigDecimal("249.99"), "GBP", Instant.now().minus(2, ChronoUnit.DAYS),
                ClaimType.UNRECOGNISED_TRANSACTION);
    }

    public static CreateCaseRequest validCreateCaseRequest() {
        return new CreateCaseRequest("cus_A1b2C3d4", "****4242", validTransactionRequest(),
                "I do not recognise this purchase.", true, true);
    }

    public static TransactionClaim transactionClaim() {
        return new TransactionClaim("txn_000123", "Synthetic Electronics Ltd", "5732", new BigDecimal("249.99"),
                Currency.getInstance("GBP"), TRANSACTION_AT, ClaimType.UNRECOGNISED_TRANSACTION);
    }

    public static PolicyCitation policyCitation() {
        return new PolicyCitation("POL-FRAUD-001", "Unauthorised Transaction Handling", "2.1",
                LocalDate.of(2026, 1, 15), "4.2 Card-not-present claims", "Fraud Operations",
                "POL-FRAUD-001#c12", 0.8731,
                "Card-not-present claims with a new device must be routed to fraud investigation.");
    }

    public static FraudCaseRecommendation recommendation(String caseId, List<String> evidenceIds) {
        return new FraudCaseRecommendation(caseId, Classification.UNAUTHORISED_TRANSACTION, RiskLevel.HIGH,
                Route.FRAUD_INVESTIGATION, new BigDecimal("0.820"), true, evidenceIds, List.of(policyCitation()),
                List.of(), List.of("rule:new-device", "trace-1"), "New device and denied merchant indicate fraud.",
                ProcessorMode.RULES);
    }
}
