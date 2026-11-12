package com.capstone.fraudcase.tools.synthetic;

import com.capstone.fraudcase.domain.EvidenceTool;
import com.capstone.fraudcase.tools.ToolFailure;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * Named synthetic scenarios, selected by a transaction-reference prefix (case-insensitive), so test-plan scenarios
 * reproduce exactly. Any other reference uses {@link #DERIVED}: every signal is derived from the identifier hash.
 */
public enum SyntheticScenario {
    /** T01: new untrusted device, impossible travel, high-risk unknown merchant, no strong authentication. */
    FRAUD("txn_fraud"),
    /** T02: recognised merchant, established device, open fulfilment complaints. */
    MERCHANT_DISPUTE("txn_dispute"),
    /** T03: the same fingerprint was posted twice. */
    DUPLICATE("txn_dup"),
    /** T04: the ledger has no record of the transaction. */
    MISSING_TRANSACTION("txn_missing"),
    /** T06: fraud indicators and mitigating indicators at the same time. */
    CONTRADICTORY("txn_conflict"),
    /** Established device, home location, recognised merchant, chip and PIN. */
    GENUINE("txn_genuine"),
    /** T08: fraud-like case whose merchant risk lookup times out. */
    TOOL_TIMEOUT("txn_timeout"),
    /** T08: device and location sources are unavailable. */
    TOOLS_UNAVAILABLE("txn_unavail"),
    /** Genuine-looking case with no recent location history (incomplete evidence). */
    PARTIAL_HISTORY("txn_partial"),
    DERIVED(null);

    private final String prefix;

    SyntheticScenario(String prefix) {
        this.prefix = prefix;
    }

    public String prefix() {
        return prefix;
    }

    public static SyntheticScenario forReference(String transactionReference) {
        if (transactionReference == null) {
            return DERIVED;
        }
        String normalized = transactionReference.toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(s -> s.prefix != null && normalized.startsWith(s.prefix))
                .findFirst()
                .orElse(DERIVED);
    }

    /** Deterministic failure injected for a tool in this scenario, if any. */
    public Optional<ToolFailure> failureFor(EvidenceTool tool) {
        return Optional.ofNullable(switch (this) {
            case MISSING_TRANSACTION -> tool == EvidenceTool.TRANSACTION_DETAILS ? ToolFailure.NOT_FOUND : null;
            case TOOL_TIMEOUT -> tool == EvidenceTool.MERCHANT_RISK_PROFILE ? ToolFailure.TIMEOUT : null;
            case TOOLS_UNAVAILABLE -> tool == EvidenceTool.DEVICE_SIGNALS || tool == EvidenceTool.RECENT_LOCATIONS
                    ? ToolFailure.UNAVAILABLE : null;
            default -> null;
        });
    }

    boolean fraudLike() {
        return this == FRAUD || this == TOOL_TIMEOUT || this == TOOLS_UNAVAILABLE;
    }
}
