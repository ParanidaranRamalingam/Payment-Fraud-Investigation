package com.capstone.fraudcase.agent.common;

import com.capstone.fraudcase.domain.ClaimType;
import com.capstone.fraudcase.domain.DomainValidationException;
import com.capstone.fraudcase.domain.EvidenceItem;
import com.capstone.fraudcase.domain.EvidenceStatus;
import com.capstone.fraudcase.domain.EvidenceTool;
import com.capstone.fraudcase.domain.ProvenanceType;
import com.capstone.fraudcase.support.SyntheticCases;
import com.capstone.fraudcase.tools.model.DeviceSignals;
import com.capstone.fraudcase.tools.model.PriorDisputes;
import com.capstone.fraudcase.tools.model.TransactionDetails;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FraudCaseContextTest {

    private static final PriorDisputes DISPUTES = new PriorDisputes(1, 0, 1, 0);

    private static EvidenceItem available(String caseId, PriorDisputes payload) {
        return new EvidenceItem("ev-1", caseId, EvidenceTool.PRIOR_DISPUTES, ProvenanceType.TOOL_RESULT,
                EvidenceStatus.AVAILABLE, payload.summary(), "trace-1", SyntheticCases.AS_OF, "test:v1",
                payload.contentHash(), payload.canonicalForm());
    }

    @Test
    void t10_evidenceFromAnotherCase_rejected() {
        CollectedEvidence fromCaseA = new CollectedEvidence(available("case-A", DISPUTES), DISPUTES);

        assertThatThrownBy(() -> new FraudCaseContext("case-B", null, "s", List.of(fromCaseA), List.of()))
                .isInstanceOf(DomainValidationException.class).hasMessageContaining("another case");
    }

    @Test
    void payload_typedLookup_returnsOnlyAvailableMatchingType() {
        FraudCaseContext context = SyntheticCases.context("txn_timeout_001", ClaimType.UNRECOGNISED_TRANSACTION);

        assertThat(context.payload(EvidenceTool.DEVICE_SIGNALS, DeviceSignals.class)).isPresent();
        assertThat(context.payload(EvidenceTool.DEVICE_SIGNALS, TransactionDetails.class)).isEmpty();
        assertThat(context.payload(EvidenceTool.MERCHANT_RISK_PROFILE)).isEmpty();
        assertThat(context.hasEvidence(EvidenceTool.MERCHANT_RISK_PROFILE)).isTrue();
    }

    @Test
    void unavailableEvidence_andGaps_describeFailedTools() {
        FraudCaseContext context = SyntheticCases.context("txn_unavail_001", ClaimType.UNRECOGNISED_TRANSACTION);

        assertThat(context.unavailableEvidence()).extracting(EvidenceItem::tool)
                .containsExactly(EvidenceTool.DEVICE_SIGNALS, EvidenceTool.RECENT_LOCATIONS);
        assertThat(context.evidenceGaps()).containsExactly(
                "getDeviceSignals returned no evidence (UNAVAILABLE)",
                "compareRecentLocations returned no evidence (UNAVAILABLE)");
    }

    @Test
    void lists_areDefensivelyCopiedAndNullSafe() {
        FraudCaseContext context = new FraudCaseContext("case-1", null, null, null, null);

        assertThat(context.evidence()).isEmpty();
        assertThat(context.policyCitations()).isEmpty();
        assertThat(context.toolTraceIds()).isEmpty();
    }

    @Test
    void blankCaseId_rejected() {
        assertThatThrownBy(() -> new FraudCaseContext(" ", null, null, List.of(), List.of()))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    void collectedEvidence_availableToolResultWithoutPayload_rejected() {
        assertThatThrownBy(() -> new CollectedEvidence(available("case-1", DISPUTES), null))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    void collectedEvidence_payloadOfDifferentTool_rejected() {
        DeviceSignals device = new DeviceSignals("dev_abcdefabcdef", SyntheticCases.AS_OF, false, true, false, 1);

        assertThatThrownBy(() -> new CollectedEvidence(available("case-1", DISPUTES), device))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    void collectedEvidence_payloadNotMatchingStoredHash_rejected() {
        assertThatThrownBy(() -> new CollectedEvidence(available("case-1", DISPUTES), new PriorDisputes(2, 0, 2, 0)))
                .isInstanceOf(DomainValidationException.class).hasMessageContaining("hash");
    }

    @Test
    void collectedEvidence_unavailableWithPayload_rejected() {
        EvidenceItem timeout = new EvidenceItem("ev-1", "case-1", EvidenceTool.PRIOR_DISPUTES,
                ProvenanceType.TOOL_RESULT, EvidenceStatus.TIMEOUT, "timed out", "trace-1", SyntheticCases.AS_OF);

        assertThatThrownBy(() -> new CollectedEvidence(timeout, DISPUTES)).isInstanceOf(DomainValidationException.class);
    }
}
