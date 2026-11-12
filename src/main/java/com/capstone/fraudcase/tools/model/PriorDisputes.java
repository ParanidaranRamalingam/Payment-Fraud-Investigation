package com.capstone.fraudcase.tools.model;

import com.capstone.fraudcase.domain.EvidenceTool;

import java.util.Map;

import static com.capstone.fraudcase.tools.model.Attributes.range;

/** Result of {@code getPriorDisputes}: the customer's dispute history over the last 12 months. */
public record PriorDisputes(
        int totalLast12Months,
        int confirmedFraudLast12Months,
        int merchantDisputesLast12Months,
        int declinedLast12Months) implements EvidencePayload {

    public PriorDisputes {
        range(totalLast12Months, 0, 1000, "totalLast12Months");
        range(confirmedFraudLast12Months, 0, 1000, "confirmedFraudLast12Months");
        range(merchantDisputesLast12Months, 0, 1000, "merchantDisputesLast12Months");
        range(declinedLast12Months, 0, 1000, "declinedLast12Months");
        if (confirmedFraudLast12Months + merchantDisputesLast12Months + declinedLast12Months > totalLast12Months) {
            throw new IllegalArgumentException("dispute breakdown exceeds total");
        }
    }

    @Override
    public EvidenceTool tool() {
        return EvidenceTool.PRIOR_DISPUTES;
    }

    @Override
    public String summary() {
        return "%d dispute(s) in the last 12 months: %d confirmed fraud, %d merchant dispute(s), %d declined."
                .formatted(totalLast12Months, confirmedFraudLast12Months, merchantDisputesLast12Months,
                        declinedLast12Months);
    }

    @Override
    public Map<String, String> attributes() {
        return Attributes.of("totalLast12Months", totalLast12Months,
                "confirmedFraudLast12Months", confirmedFraudLast12Months,
                "merchantDisputesLast12Months", merchantDisputesLast12Months,
                "declinedLast12Months", declinedLast12Months);
    }
}
