package com.capstone.fraudcase.domain;

/**
 * Allow-list of deterministic evidence tools. Anything not listed here must not be invoked.
 */
public enum EvidenceTool {
    TRANSACTION_DETAILS("getTransactionDetails"),
    DEVICE_SIGNALS("getDeviceSignals"),
    RECENT_LOCATIONS("compareRecentLocations"),
    MERCHANT_RISK_PROFILE("getMerchantRiskProfile"),
    PRIOR_DISPUTES("getPriorDisputes"),
    CASE_SLA("calculateCaseSla"),
    POLICY_RETRIEVAL("retrievePolicy");

    private final String toolId;

    EvidenceTool(String toolId) {
        this.toolId = toolId;
    }

    public String toolId() {
        return toolId;
    }
}
