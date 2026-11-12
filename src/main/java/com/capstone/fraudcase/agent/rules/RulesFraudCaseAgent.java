package com.capstone.fraudcase.agent.rules;

import com.capstone.fraudcase.agent.common.FraudCaseContext;
import com.capstone.fraudcase.agent.common.FraudCaseProcessor;
import com.capstone.fraudcase.agent.common.RecommendationValidator;
import com.capstone.fraudcase.domain.ClaimType;
import com.capstone.fraudcase.domain.Classification;
import com.capstone.fraudcase.domain.EvidenceTool;
import com.capstone.fraudcase.domain.FraudCaseRecommendation;
import com.capstone.fraudcase.domain.ProcessorMode;
import com.capstone.fraudcase.domain.RiskLevel;
import com.capstone.fraudcase.domain.Route;
import com.capstone.fraudcase.tools.model.MerchantRiskProfile;
import com.capstone.fraudcase.tools.model.TransactionDetails;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Deterministic rules processor: runs the complete analysis without any model. Rules are evaluated in a fixed
 * order and the first match decides classification and route; risk and confidence follow from the indicators
 * and evidence gaps. The customer statement is never parsed for instructions.
 *
 * <pre>
 * R-DATA-01     no transaction claim                         -> INSUFFICIENT_EVIDENCE / MANUAL_REVIEW
 * R-DATA-02     transaction details unavailable              -> INSUFFICIENT_EVIDENCE / MANUAL_REVIEW
 * R-DATA-03     2+ of device/location/merchant unavailable   -> INSUFFICIENT_EVIDENCE / MANUAL_REVIEW
 * R-DUP-01      duplicate posting, &lt;2 fraud indicators       -> DUPLICATE_PROCESSING / PAYMENT_OPERATIONS
 * R-DUP-02      duplicate claimed, single posting            -> GENUINE_TRANSACTION / MANUAL_REVIEW
 * R-CONFLICT-01 2+ fraud and 2+ mitigating indicators        -> SUSPICIOUS_TRANSACTION / MANUAL_REVIEW
 * R-MERCHANT-01 merchant issue claimed, &lt;2 fraud indicators  -> MERCHANT_DISPUTE / DISPUTE_OPERATIONS
 * R-FRAUD-01    unrecognised, 3+ fraud indicators            -> UNAUTHORISED_TRANSACTION / FRAUD_INVESTIGATION
 * R-FRAUD-02    2+ fraud indicators                          -> SUSPICIOUS_TRANSACTION / FRAUD_INVESTIGATION
 * R-GENUINE-01  no fraud and 2+ mitigating indicators        -> GENUINE_TRANSACTION / MANUAL_REVIEW
 * R-REVIEW-01   exactly one fraud indicator                  -> SUSPICIOUS_TRANSACTION / MANUAL_REVIEW
 * R-DATA-04     nothing decisive                             -> INSUFFICIENT_EVIDENCE / MANUAL_REVIEW
 * </pre>
 */
@Component
public class RulesFraudCaseAgent implements FraudCaseProcessor {

    public static final String RULESET_VERSION = "rules-v1";
    static final BigDecimal HIGH_VALUE_THRESHOLD = new BigDecimal("1000");
    static final BigDecimal GAP_PENALTY = new BigDecimal("0.10");
    static final BigDecimal PARTIAL_HISTORY_PENALTY = new BigDecimal("0.05");
    static final BigDecimal MIN_CONFIDENCE = new BigDecimal("0.05");
    static final BigDecimal MAX_CONFIDENCE = new BigDecimal("0.95");

    private static final List<EvidenceTool> SIGNAL_TOOLS = List.of(
            EvidenceTool.DEVICE_SIGNALS, EvidenceTool.RECENT_LOCATIONS, EvidenceTool.MERCHANT_RISK_PROFILE);

    private record Decision(String ruleId, Classification classification, Route route, RiskLevel risk,
                            String baseConfidence, String finding, List<String> missing, FraudSignals signals) {
    }

    @Override
    public FraudCaseRecommendation analyze(FraudCaseContext context) {
        Objects.requireNonNull(context, "context");
        return build(context, decide(context));
    }

    private Decision decide(FraudCaseContext context) {
        if (context.claim() == null) {
            return insufficient("R-DATA-01", "No transaction claim is on file for this case.",
                    "transaction claim (reference, merchant, amount, date)", null);
        }
        Optional<TransactionDetails> found = context.payload(EvidenceTool.TRANSACTION_DETAILS, TransactionDetails.class);
        if (found.isEmpty()) {
            return insufficient("R-DATA-02", "The disputed transaction could not be confirmed in the ledger.",
                    "confirmed ledger record of the disputed transaction", null);
        }
        List<String> missingSignals = SIGNAL_TOOLS.stream()
                .filter(tool -> context.payload(tool).isEmpty())
                .map(EvidenceTool::toolId)
                .toList();
        if (missingSignals.size() >= 2) {
            return insufficient("R-DATA-03", "Too few risk signals were available to classify the transaction ("
                    + String.join(", ", missingSignals) + " missing).", "device, location and merchant risk signals", null);
        }

        TransactionDetails transaction = found.get();
        FraudSignals signals = FraudSignals.evaluate(context, transaction);
        int fraud = signals.fraud().size();
        int mitigating = signals.mitigating().size();
        ClaimType claimType = context.claim().claimType();
        boolean highValue = context.claim().amount().compareTo(HIGH_VALUE_THRESHOLD) >= 0;
        RiskLevel fraudRisk = fraudRisk(fraud, highValue);
        Optional<MerchantRiskProfile> merchant =
                context.payload(EvidenceTool.MERCHANT_RISK_PROFILE, MerchantRiskProfile.class);

        if (transaction.duplicatePosting() && fraud < 2) {
            return new Decision("R-DUP-01", Classification.DUPLICATE_PROCESSING, Route.PAYMENT_OPERATIONS,
                    highValue ? RiskLevel.MEDIUM : RiskLevel.LOW, "0.85",
                    transaction.matchingPostings() + " postings share the same transaction fingerprint.",
                    List.of(), signals);
        }
        if (claimType == ClaimType.DUPLICATE_CHARGE && fraud < 2) {
            return new Decision("R-DUP-02", Classification.GENUINE_TRANSACTION, Route.MANUAL_REVIEW, RiskLevel.LOW,
                    "0.55", "A duplicate charge was reported but only one posting carries this fingerprint.",
                    List.of("statement or ledger extract showing the second posting"), signals);
        }
        if (fraud >= 2 && mitigating >= 2) {
            return new Decision("R-CONFLICT-01", Classification.SUSPICIOUS_TRANSACTION, Route.MANUAL_REVIEW,
                    max(RiskLevel.MEDIUM, fraudRisk), "0.40", "Fraud indicators and mitigating indicators conflict.",
                    List.of("Analyst to resolve contradictory signals: " + ids(signals.fraud()) + " vs "
                            + ids(signals.mitigating())), signals);
        }
        if (claimType == ClaimType.MERCHANT_ISSUE && fraud < 2) {
            boolean recognised = merchant.map(MerchantRiskProfile::recognisedByCustomer).orElse(false);
            boolean riskyMerchant = merchant.map(m -> m.riskTier() == MerchantRiskProfile.RiskTier.HIGH).orElse(false);
            return new Decision("R-MERCHANT-01", Classification.MERCHANT_DISPUTE, Route.DISPUTE_OPERATIONS,
                    riskyMerchant ? RiskLevel.MEDIUM : RiskLevel.LOW, recognised ? "0.80" : "0.65",
                    recognised ? "A merchant issue was reported for a merchant the customer has used before."
                            : "A merchant issue was reported; no prior customer relationship with the merchant is on file.",
                    recognised ? List.of() : List.of("merchant response to the customer's complaint"), signals);
        }
        if (fraud >= 3 && claimType == ClaimType.UNRECOGNISED_TRANSACTION) {
            return new Decision("R-FRAUD-01", Classification.UNAUTHORISED_TRANSACTION, Route.FRAUD_INVESTIGATION,
                    fraudRisk, "0.85", "An unrecognised transaction shows multiple independent fraud indicators.",
                    List.of(), signals);
        }
        if (fraud >= 2) {
            return new Decision("R-FRAUD-02", Classification.SUSPICIOUS_TRANSACTION, Route.FRAUD_INVESTIGATION,
                    fraudRisk, "0.65", "Multiple fraud indicators are present.", List.of(), signals);
        }
        if (fraud == 0 && mitigating >= 2) {
            return new Decision("R-GENUINE-01", Classification.GENUINE_TRANSACTION, Route.MANUAL_REVIEW,
                    RiskLevel.LOW, "0.60",
                    "No fraud indicators; the transaction matches the customer's established behaviour.",
                    List.of(), signals);
        }
        if (fraud == 1) {
            return new Decision("R-REVIEW-01", Classification.SUSPICIOUS_TRANSACTION, Route.MANUAL_REVIEW,
                    fraudRisk, "0.45", "A single uncorroborated fraud indicator is present.", List.of(), signals);
        }
        return insufficient("R-DATA-04", "No decisive fraud or mitigating indicators were found.",
                "additional evidence on whether the customer authorised the transaction", signals);
    }

    private static Decision insufficient(String ruleId, String finding, String missing, FraudSignals signals) {
        return new Decision(ruleId, Classification.INSUFFICIENT_EVIDENCE, Route.MANUAL_REVIEW, RiskLevel.MEDIUM,
                "0.25", finding, List.of(missing), signals);
    }

    private static FraudCaseRecommendation build(FraudCaseContext context, Decision decision) {
        List<String> gaps = context.evidenceGaps();
        boolean partialHistory = decision.signals() != null && decision.signals().locationHistoryMissing();

        Set<String> missing = new LinkedHashSet<>(decision.missing());
        missing.addAll(gaps);
        if (partialHistory) {
            missing.add("recent location history (none on file)");
        }

        BigDecimal confidence = new BigDecimal(decision.baseConfidence())
                .subtract(GAP_PENALTY.multiply(BigDecimal.valueOf(gaps.size())))
                .subtract(partialHistory ? PARTIAL_HISTORY_PENALTY : BigDecimal.ZERO)
                .max(MIN_CONFIDENCE).min(MAX_CONFIDENCE)
                .setScale(3, RoundingMode.HALF_UP);

        List<String> traceIds = new ArrayList<>();
        traceIds.add(RecommendationValidator.RULE_TRACE_PREFIX + decision.ruleId());
        if (decision.signals() != null) {
            decision.signals().present().forEach(s -> traceIds.add(RecommendationValidator.SIGNAL_TRACE_PREFIX + s.id()));
        }
        traceIds.addAll(context.toolTraceIds());

        return new FraudCaseRecommendation(context.caseId(), decision.classification(), decision.risk(),
                decision.route(), confidence, true, context.evidenceIds(), context.policyCitations(),
                List.copyOf(missing), traceIds, rationale(decision, gaps), ProcessorMode.RULES);
    }

    private static String rationale(Decision decision, List<String> gaps) {
        StringBuilder sb = new StringBuilder()
                .append(decision.ruleId()).append(" (").append(RULESET_VERSION).append("): ")
                .append(decision.finding());
        if (decision.signals() != null) {
            List<RuleSignal> fraud = decision.signals().fraud();
            List<RuleSignal> mitigating = decision.signals().mitigating();
            sb.append(" Fraud indicators (").append(fraud.size()).append("): ").append(descriptions(fraud))
                    .append(". Mitigating indicators (").append(mitigating.size()).append("): ")
                    .append(descriptions(mitigating)).append('.');
        }
        if (!gaps.isEmpty()) {
            sb.append(" Evidence gaps: ").append(String.join("; ", gaps)).append('.');
        }
        sb.append(" Suggested route ").append(decision.route()).append("; advisory only, human review required.");
        String text = sb.toString();
        return text.length() <= FraudCaseRecommendation.MAX_RATIONALE_LENGTH
                ? text : text.substring(0, FraudCaseRecommendation.MAX_RATIONALE_LENGTH);
    }

    static RiskLevel fraudRisk(int fraudIndicators, boolean highValue) {
        RiskLevel base = fraudIndicators >= 5 ? RiskLevel.CRITICAL
                : fraudIndicators >= 3 ? RiskLevel.HIGH
                : fraudIndicators >= 1 ? RiskLevel.MEDIUM
                : RiskLevel.LOW;
        if (highValue && fraudIndicators >= 1 && base != RiskLevel.CRITICAL) {
            return RiskLevel.values()[base.ordinal() + 1];
        }
        return base;
    }

    private static RiskLevel max(RiskLevel a, RiskLevel b) {
        return a.compareTo(b) >= 0 ? a : b;
    }

    private static String ids(List<RuleSignal> signals) {
        return signals.stream().map(RuleSignal::id).collect(Collectors.joining(", ", "[", "]"));
    }

    private static String descriptions(List<RuleSignal> signals) {
        return signals.isEmpty() ? "none" : signals.stream().map(RuleSignal::description).collect(Collectors.joining(", "));
    }
}
