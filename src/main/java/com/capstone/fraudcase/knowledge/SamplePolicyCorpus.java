package com.capstone.fraudcase.knowledge;

import java.time.LocalDate;
import java.util.List;

/**
 * Bundled synthetic policy corpus used to demonstrate grounded retrieval in environments with no external
 * policy source. Content is fictional training data: it uses only the project vocabulary, names no real
 * regulation and carries the INTERNAL classification. {@code pol-unauthorized-txn} ships two versions so
 * effective-version filtering is visible in the demo.
 */
public final class SamplePolicyCorpus {

    private SamplePolicyCorpus() {
    }

    public static List<PolicyDocument> documents() {
        return List.of(unauthorizedV1(), unauthorizedV2(), merchantDispute(), duplicateCharge(),
                manualReview());
    }

    private static PolicyDocument unauthorizedV1() {
        return new PolicyDocument("pol-unauthorized-txn", "Unauthorized Transaction Handling", "1.0",
                LocalDate.of(2025, 1, 1), "Fraud Operations Policy", PolicyClassification.INTERNAL,
                PolicyStatus.APPROVED, List.of(
                new PolicyDocument.Section("Scope",
                        "This policy covers unauthorized card transactions reported by the cardholder, "
                                + "including fraud where the card was compromised or the device was new."),
                new PolicyDocument.Section("Procedure",
                        "When a cardholder reports an unauthorized transaction, confirm the fraud indicators "
                                + "and route the case to the fraud investigation queue.")));
    }

    private static PolicyDocument unauthorizedV2() {
        return new PolicyDocument("pol-unauthorized-txn", "Unauthorized Transaction Handling", "2.0",
                LocalDate.of(2026, 1, 1), "Fraud Operations Policy", PolicyClassification.INTERNAL,
                PolicyStatus.APPROVED, List.of(
                new PolicyDocument.Section("Scope",
                        "This policy covers unauthorized transactions: card charges the cardholder reports "
                                + "as fraud, including compromised card and unrecognized merchant activity."),
                new PolicyDocument.Section("Procedure",
                        "An unauthorized transaction report requires fraud investigation. Verify device, "
                                + "location and merchant risk evidence, then route to fraud operations."),
                new PolicyDocument.Section("Timeline",
                        "The cardholder must report the unauthorized transaction within the statement "
                                + "timeline; late reports still receive a manual review.")));
    }

    private static PolicyDocument merchantDispute() {
        return new PolicyDocument("pol-merchant-dispute", "Merchant Dispute and Chargeback Guide", "1.2",
                LocalDate.of(2025, 6, 1), "Dispute Operations Policy", PolicyClassification.INTERNAL,
                PolicyStatus.APPROVED, List.of(
                new PolicyDocument.Section("Scope",
                        "Covers merchant disputes: goods or services not received, defective goods, and "
                                + "cancellation or refund disagreements raised with the merchant."),
                new PolicyDocument.Section("Procedure",
                        "For a merchant dispute, request the cancellation or refund evidence and route to "
                                + "dispute operations for a chargeback assessment.")));
    }

    private static PolicyDocument duplicateCharge() {
        return new PolicyDocument("pol-duplicate-charge", "Duplicate Charge Correction", "2.0",
                LocalDate.of(2025, 3, 15), "Payment Operations Policy", PolicyClassification.INTERNAL,
                PolicyStatus.APPROVED, List.of(
                new PolicyDocument.Section("Scope",
                        "Covers duplicate transaction postings: the same charge appearing twice on the "
                                + "cardholder statement or ledger."),
                new PolicyDocument.Section("Procedure",
                        "Confirm the duplicate posting fingerprint, then route to payment operations for a "
                                + "reversal correction of the extra transaction.")));
    }

    private static PolicyDocument manualReview() {
        return new PolicyDocument("pol-manual-review", "Manual Review and Escalation", "1.0",
                LocalDate.of(2025, 1, 15), "Investigation Standards", PolicyClassification.INTERNAL,
                PolicyStatus.APPROVED, List.of(
                new PolicyDocument.Section("Escalation",
                        "When evidence is missing, conflicting, or no approved policy passage applies, the "
                                + "case must escalate to a manual review by an analyst.")));
    }
}
