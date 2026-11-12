package com.capstone.fraudcase.support;

import com.capstone.fraudcase.agent.common.CollectedEvidence;
import com.capstone.fraudcase.agent.common.FraudCaseContext;
import com.capstone.fraudcase.application.InvestigationOrchestrator;
import com.capstone.fraudcase.domain.ClaimType;
import com.capstone.fraudcase.domain.EvidenceItem;
import com.capstone.fraudcase.domain.EvidenceTool;
import com.capstone.fraudcase.domain.ProvenanceType;
import com.capstone.fraudcase.domain.TransactionClaim;
import com.capstone.fraudcase.tools.EvidenceToolProvider;
import com.capstone.fraudcase.tools.ToolInvoker;
import com.capstone.fraudcase.tools.ToolOutcome;
import com.capstone.fraudcase.tools.ToolProperties;
import com.capstone.fraudcase.tools.ToolRequest;
import com.capstone.fraudcase.tools.sla.CaseSlaCalculator;
import com.capstone.fraudcase.tools.synthetic.SyntheticEvidenceTool;
import com.capstone.fraudcase.tools.synthetic.SyntheticFixtureGenerator;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;

/** Builds deterministic synthetic requests and fully-populated case contexts without a database. */
public final class SyntheticCases {

    public static final String CUSTOMER = "cus_A1b2C3d4";
    public static final String CARD = "****4242";
    public static final Instant TRANSACTION_AT = Instant.parse("2026-10-01T09:15:30Z");
    public static final Instant CASE_CREATED_AT = Instant.parse("2026-10-02T08:00:00Z");
    public static final Instant AS_OF = Instant.parse("2026-10-02T10:00:00Z");
    public static final Clock FIXED_CLOCK = Clock.fixed(AS_OF, ZoneOffset.UTC);

    private SyntheticCases() {
    }

    public static TransactionClaim claim(String transactionReference, ClaimType claimType, BigDecimal amount) {
        return new TransactionClaim(transactionReference, "Synthetic Electronics Ltd", "5732", amount,
                Currency.getInstance("GBP"), TRANSACTION_AT, claimType);
    }

    public static ToolRequest request(String transactionReference, ClaimType claimType) {
        return request(transactionReference, claimType, new BigDecimal("249.99"));
    }

    public static ToolRequest request(String transactionReference, ClaimType claimType, BigDecimal amount) {
        return new ToolRequest("case-" + transactionReference, CUSTOMER, CARD,
                claim(transactionReference, claimType, amount), CASE_CREATED_AT, AS_OF);
    }

    public static List<EvidenceToolProvider> providers() {
        SyntheticFixtureGenerator fixtures = new SyntheticFixtureGenerator();
        return List.of(
                new SyntheticEvidenceTool(EvidenceTool.TRANSACTION_DETAILS, fixtures.source(), fixtures::transactionDetails),
                new SyntheticEvidenceTool(EvidenceTool.DEVICE_SIGNALS, fixtures.source(), fixtures::deviceSignals),
                new SyntheticEvidenceTool(EvidenceTool.RECENT_LOCATIONS, fixtures.source(), fixtures::recentLocations),
                new SyntheticEvidenceTool(EvidenceTool.MERCHANT_RISK_PROFILE, fixtures.source(), fixtures::merchantRiskProfile),
                new SyntheticEvidenceTool(EvidenceTool.PRIOR_DISPUTES, fixtures.source(), fixtures::priorDisputes),
                new CaseSlaCalculator());
    }

    public static ToolInvoker invoker() {
        return new ToolInvoker(providers(), ToolProperties.withTimeout(Duration.ofSeconds(2)), FIXED_CLOCK);
    }

    /** Runs the full evidence plan for the request and wraps results as case-scoped evidence. */
    public static FraudCaseContext context(ToolRequest request, String customerStatement) {
        try (ToolInvoker invoker = invoker()) {
            List<CollectedEvidence> collected = new ArrayList<>();
            int n = 0;
            for (EvidenceTool tool : InvestigationOrchestrator.EVIDENCE_PLAN) {
                n++;
                ToolOutcome outcome = invoker.invoke(tool, request);
                collected.add(new CollectedEvidence(new EvidenceItem("ev-" + n, request.caseId(), tool,
                        ProvenanceType.TOOL_RESULT, outcome.evidenceStatus(), outcome.summary(), "trace-" + n,
                        outcome.completedAt(), outcome.source(),
                        outcome.isAvailable() ? outcome.payload().contentHash() : null,
                        outcome.isAvailable() ? outcome.payload().canonicalForm() : null), outcome.payload()));
            }
            return new FraudCaseContext(request.caseId(), request.claim(), customerStatement, collected, List.of());
        }
    }

    public static FraudCaseContext context(String transactionReference, ClaimType claimType) {
        return context(request(transactionReference, claimType), "I do not recognise this purchase.");
    }
}
