package com.capstone.fraudcase.agent.springai;

import com.capstone.fraudcase.agent.common.FraudCaseContext;
import com.capstone.fraudcase.domain.ClaimType;
import com.capstone.fraudcase.domain.EvidenceTool;
import com.capstone.fraudcase.support.SyntheticCases;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CaseEvidenceToolsTest {

    @Test
    void returnsCanonicalPayloadForAvailableEvidence() {
        FraudCaseContext context = SyntheticCases.context("txn_fraud_1234", ClaimType.UNRECOGNISED_TRANSACTION);
        CaseEvidenceTools tools = new CaseEvidenceTools(context);

        String details = tools.getTransactionDetails();

        assertThat(details).contains("evidenceId=").contains("toolTraceId=").contains("status=AVAILABLE");
        assertThat(details).contains(context.evidenceIds().getFirst());
    }

    @Test
    void unavailableEvidenceReportsItsSummaryOnly() {
        FraudCaseContext context = SyntheticCases.context("txn_unavail_1", ClaimType.UNRECOGNISED_TRANSACTION);
        CaseEvidenceTools tools = new CaseEvidenceTools(context);

        String result = tools.getDeviceSignals();

        var item = context.evidence().stream()
                .filter(e -> e.item().tool() == EvidenceTool.DEVICE_SIGNALS).findFirst().orElseThrow().item();
        assertThat(item.isAvailable()).isFalse();
        assertThat(result).isEqualTo(item.summary());
    }

    @Test
    void neverFabricatesEvidenceForAbsentTools() {
        FraudCaseContext context = SyntheticCases.context("txn_fraud_1234", ClaimType.UNRECOGNISED_TRANSACTION);
        CaseEvidenceTools tools = new CaseEvidenceTools(context);

        for (EvidenceTool tool : EvidenceTool.values()) {
            if (tool == EvidenceTool.POLICY_RETRIEVAL) {
                continue;
            }
            assertThat(context.evidence().stream().filter(e -> e.item().tool() == tool)).isNotEmpty();
        }
        // POLICY_RETRIEVAL is served by the knowledge layer and is not exposed as a model tool
        assertThat(tools.getClass().getDeclaredMethods())
                .noneMatch(m -> m.getName().equalsIgnoreCase("retrievePolicy"));
    }
}
