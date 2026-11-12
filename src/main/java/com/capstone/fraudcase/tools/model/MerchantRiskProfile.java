package com.capstone.fraudcase.tools.model;

import com.capstone.fraudcase.domain.EvidenceTool;

import java.math.BigDecimal;
import java.util.Map;

import static com.capstone.fraudcase.tools.model.Attributes.range;
import static com.capstone.fraudcase.tools.model.Attributes.require;

/** Result of {@code getMerchantRiskProfile}, including the customer's history with the merchant. */
public record MerchantRiskProfile(
        String merchantName,
        String merchantCategoryCode,
        RiskTier riskTier,
        BigDecimal chargebackRatePercent,
        int priorCustomerTransactions,
        int openFulfilmentComplaints) implements EvidencePayload {

    public enum RiskTier { LOW, MEDIUM, HIGH }

    public MerchantRiskProfile {
        require(merchantName, "merchantName");
        require(riskTier, "riskTier");
        require(chargebackRatePercent, "chargebackRatePercent");
        if (chargebackRatePercent.signum() < 0 || chargebackRatePercent.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("chargebackRatePercent must be between 0 and 100");
        }
        range(priorCustomerTransactions, 0, 10_000, "priorCustomerTransactions");
        range(openFulfilmentComplaints, 0, 10_000, "openFulfilmentComplaints");
    }

    public boolean recognisedByCustomer() {
        return priorCustomerTransactions > 0;
    }

    @Override
    public EvidenceTool tool() {
        return EvidenceTool.MERCHANT_RISK_PROFILE;
    }

    @Override
    public String summary() {
        return "Merchant %s (MCC %s): %s risk tier, chargeback rate %s%%, %d prior transaction(s) by this customer, %d open fulfilment complaint(s)."
                .formatted(merchantName, merchantCategoryCode == null ? "n/a" : merchantCategoryCode, riskTier,
                        chargebackRatePercent.toPlainString(), priorCustomerTransactions, openFulfilmentComplaints);
    }

    @Override
    public Map<String, String> attributes() {
        return Attributes.of("merchantName", merchantName, "merchantCategoryCode", merchantCategoryCode,
                "riskTier", riskTier, "chargebackRatePercent", chargebackRatePercent.toPlainString(),
                "priorCustomerTransactions", priorCustomerTransactions,
                "openFulfilmentComplaints", openFulfilmentComplaints);
    }
}
