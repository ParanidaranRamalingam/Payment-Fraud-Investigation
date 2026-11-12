package com.capstone.fraudcase.tools;

import com.capstone.fraudcase.domain.ClaimType;
import com.capstone.fraudcase.domain.ContentHash;
import com.capstone.fraudcase.domain.EvidenceItem;
import com.capstone.fraudcase.domain.EvidenceTool;
import com.capstone.fraudcase.domain.SensitiveDataGuard;
import com.capstone.fraudcase.support.SyntheticCases;
import com.capstone.fraudcase.tools.model.EvidencePayload;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** Contract every registered evidence tool must honour, across scenarios and derived identifiers. */
class EvidenceToolContractTest {

    private static final String[] REFERENCES = {"txn_000123", "txn_fraud_001", "txn_dispute_001", "txn_dup_001",
            "txn_conflict_001", "txn_genuine_001", "txn_partial_001", "REF-xyz-0042", "txn_derived_77"};

    static Stream<Arguments> providerAndReference() {
        return SyntheticCases.providers().stream()
                .flatMap(p -> Stream.of(REFERENCES).map(ref -> Arguments.of(p, ref)));
    }

    static Stream<EvidenceToolProvider> providers() {
        return SyntheticCases.providers().stream();
    }

    @Test
    void registry_coversExactlyTheAllowListedEvidenceTools() {
        assertThat(SyntheticCases.providers()).extracting(EvidenceToolProvider::tool)
                .containsExactlyInAnyOrderElementsOf(ToolInvoker.ALLOW_LIST)
                .doesNotContain(EvidenceTool.POLICY_RETRIEVAL);
    }

    @ParameterizedTest
    @MethodSource("providers")
    void source_isVersionedAndFitsColumn(EvidenceToolProvider provider) {
        assertThat(provider.source()).matches("[a-z-]+:v\\d+(:[a-z0-9-]+)?").hasSizeLessThanOrEqualTo(EvidenceItem.MAX_SOURCE_LENGTH);
    }

    @ParameterizedTest(name = "{0} / {1}")
    @MethodSource("providerAndReference")
    void fetch_returnsTypedPayloadOfItsOwnTool(EvidenceToolProvider provider, String reference) {
        EvidencePayload payload = provider.fetch(SyntheticCases.request(reference, ClaimType.UNRECOGNISED_TRANSACTION));

        assertThat(payload).isNotNull();
        assertThat(payload.tool()).isEqualTo(provider.tool());
    }

    @ParameterizedTest(name = "{0} / {1}")
    @MethodSource("providerAndReference")
    void fetch_identicalRequest_isDeterministic(EvidenceToolProvider provider, String reference) {
        ToolRequest request = SyntheticCases.request(reference, ClaimType.MERCHANT_ISSUE);

        EvidencePayload first = provider.fetch(request);
        EvidencePayload second = provider.fetch(request);

        assertThat(second).isEqualTo(first);
        assertThat(second.summary()).isEqualTo(first.summary());
        assertThat(second.contentHash()).isEqualTo(first.contentHash());
    }

    @ParameterizedTest(name = "{0} / {1}")
    @MethodSource("providerAndReference")
    void payload_summaryIsSafeBoundedAndMasked(EvidenceToolProvider provider, String reference) {
        EvidencePayload payload = provider.fetch(SyntheticCases.request(reference, ClaimType.UNRECOGNISED_TRANSACTION));

        assertThat(payload.summary()).isNotBlank().hasSizeLessThanOrEqualTo(EvidenceItem.MAX_SUMMARY_LENGTH);
        assertThat(SensitiveDataGuard.isSafeFreeText(payload.summary())).isTrue();
        assertThat(payload.summary()).doesNotContain(SyntheticCases.CUSTOMER);
    }

    @ParameterizedTest(name = "{0} / {1}")
    @MethodSource("providerAndReference")
    void payload_canonicalFormIsSortedAndHashed(EvidenceToolProvider provider, String reference) {
        EvidencePayload payload = provider.fetch(SyntheticCases.request(reference, ClaimType.DUPLICATE_CHARGE));
        Map<String, String> attributes = payload.attributes();

        assertThat(attributes).isNotEmpty().doesNotContainValue(null);
        assertThat(payload.canonicalForm().lines().toList()).isSorted().hasSize(attributes.size());
        assertThat(payload.canonicalForm()).hasSizeLessThanOrEqualTo(EvidenceItem.MAX_PAYLOAD_LENGTH);
        assertThat(payload.contentHash())
                .isEqualTo(ContentHash.sha256Hex(payload.tool().name() + "\n" + payload.canonicalForm()));
    }

    @ParameterizedTest(name = "{0} / {1}")
    @MethodSource("providerAndReference")
    void invoke_throughInvoker_succeedsAndPreservesProvenance(EvidenceToolProvider provider, String reference) {
        try (ToolInvoker invoker = SyntheticCases.invoker()) {
            ToolOutcome outcome = invoker.invoke(provider.tool(),
                    SyntheticCases.request(reference, ClaimType.UNRECOGNISED_TRANSACTION));

            assertThat(outcome.isAvailable()).isTrue();
            assertThat(outcome.source()).isEqualTo(provider.source());
            assertThat(outcome.summary()).isEqualTo(outcome.payload().summary());
        }
    }
}
