package com.capstone.fraudcase.agent.springai;

import com.capstone.fraudcase.agent.common.CollectedEvidence;
import com.capstone.fraudcase.agent.common.FraudCaseContext;
import com.capstone.fraudcase.domain.EvidenceItem;
import com.capstone.fraudcase.domain.EvidenceTool;
import org.springframework.ai.tool.annotation.Tool;

import java.util.Objects;

/**
 * Approved, read-only tools the model may call while analysing a case. Every method is bound to one
 * {@link FraudCaseContext} and only returns evidence that was already collected through {@code ToolInvoker} —
 * the model cannot invoke new tool executions, cannot reach other cases and cannot fabricate a result: a tool
 * that returned no evidence reports exactly that.
 */
public class CaseEvidenceTools {

    private final FraudCaseContext context;

    public CaseEvidenceTools(FraudCaseContext context) {
        this.context = Objects.requireNonNull(context, "context");
    }

    @Tool(description = "Return the collected getTransactionDetails evidence for this case (ledger record of the disputed transaction)")
    public String getTransactionDetails() {
        return evidence(EvidenceTool.TRANSACTION_DETAILS);
    }

    @Tool(description = "Return the collected getDeviceSignals evidence for this case (device and session risk signals)")
    public String getDeviceSignals() {
        return evidence(EvidenceTool.DEVICE_SIGNALS);
    }

    @Tool(description = "Return the collected compareRecentLocations evidence for this case (transaction location vs recent history)")
    public String compareRecentLocations() {
        return evidence(EvidenceTool.RECENT_LOCATIONS);
    }

    @Tool(description = "Return the collected getMerchantRiskProfile evidence for this case (merchant risk tier and customer history)")
    public String getMerchantRiskProfile() {
        return evidence(EvidenceTool.MERCHANT_RISK_PROFILE);
    }

    @Tool(description = "Return the collected getPriorDisputes evidence for this case (prior dispute history for the customer/card)")
    public String getPriorDisputes() {
        return evidence(EvidenceTool.PRIOR_DISPUTES);
    }

    @Tool(description = "Return the collected calculateCaseSla evidence for this case (case age and review deadline)")
    public String calculateCaseSla() {
        return evidence(EvidenceTool.CASE_SLA);
    }

    private String evidence(EvidenceTool tool) {
        return context.evidence().stream()
                .filter(e -> e.item().tool() == tool)
                .findFirst()
                .map(this::describe)
                .orElse("No " + tool.toolId() + " evidence was collected for this case.");
    }

    private String describe(CollectedEvidence collected) {
        EvidenceItem item = collected.item();
        if (!item.isAvailable() || collected.payload() == null) {
            return item.summary();
        }
        return "evidenceId=" + item.evidenceId() + "\ntoolTraceId=" + item.toolTraceId()
                + "\nstatus=" + item.status() + "\n" + collected.payload().canonicalForm();
    }
}
