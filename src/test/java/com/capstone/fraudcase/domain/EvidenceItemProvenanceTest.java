package com.capstone.fraudcase.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvidenceItemProvenanceTest {

    private static final Instant NOW = Instant.parse("2026-11-01T10:00:00Z");
    private static final String PAYLOAD = "totalLast12Months=1\n";
    private static final String HASH = ContentHash.sha256Hex("PRIOR_DISPUTES\n" + PAYLOAD);

    private static EvidenceItem item(EvidenceStatus status, String source, String hash, String payload) {
        return new EvidenceItem("ev-1", "case-1", EvidenceTool.PRIOR_DISPUTES, ProvenanceType.TOOL_RESULT, status,
                "summary", "trace-1", NOW, source, hash, payload);
    }

    @Test
    void availableToolEvidence_withSourceHashAndPayload_accepted() {
        EvidenceItem item = item(EvidenceStatus.AVAILABLE, "synthetic-fixture:v1", HASH, PAYLOAD);

        assertThat(item.source()).isEqualTo("synthetic-fixture:v1");
        assertThat(item.contentHash()).isEqualTo(HASH);
        assertThat(item.payload()).isEqualTo(PAYLOAD);
    }

    @Test
    void legacyConstructor_leavesProvenanceExtrasEmpty() {
        EvidenceItem item = new EvidenceItem("ev-1", "case-1", EvidenceTool.CASE_SLA, ProvenanceType.TOOL_RESULT,
                EvidenceStatus.TIMEOUT, "timed out", "trace-1", NOW);

        assertThat(item.source()).isNull();
        assertThat(item.contentHash()).isNull();
        assertThat(item.payload()).isNull();
    }

    @Test
    void unavailableEvidence_withPayload_rejected() {
        assertThatThrownBy(() -> item(EvidenceStatus.TIMEOUT, "s:v1", HASH, PAYLOAD))
                .isInstanceOf(DomainValidationException.class).hasMessageContaining("AVAILABLE");
    }

    @Test
    void hashWithoutPayload_rejected() {
        assertThatThrownBy(() -> item(EvidenceStatus.AVAILABLE, "s:v1", HASH, null))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    void malformedHash_rejected() {
        assertThatThrownBy(() -> item(EvidenceStatus.AVAILABLE, "s:v1", "ABC", PAYLOAD))
                .isInstanceOf(DomainValidationException.class).hasMessageContaining("SHA-256");
    }

    @Test
    void tamperedPayload_hashMismatch_rejected() {
        assertThatThrownBy(() -> item(EvidenceStatus.AVAILABLE, "s:v1", HASH, "totalLast12Months=9\n"))
                .isInstanceOf(DomainValidationException.class).hasMessageContaining("does not match");
    }

    @Test
    void overlongSource_rejected() {
        assertThatThrownBy(() -> item(EvidenceStatus.AVAILABLE, "s".repeat(65), HASH, PAYLOAD))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    void contentHash_isLowercaseSha256Hex() {
        assertThat(ContentHash.sha256Hex("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(ContentHash.isSha256Hex(ContentHash.sha256Hex("x"))).isTrue();
        assertThat(ContentHash.isSha256Hex(null)).isFalse();
    }

    @Test
    void redact_replacesUnmaskedNumbersAndControlChars_keepsMaskedReferences() {
        assertThat(SensitiveDataGuard.redact("card 4111-1111-1111-1111 masked ****4242\u0000"))
                .isEqualTo("card [REDACTED] masked ****4242");
        assertThat(SensitiveDataGuard.redact(null)).isNull();
    }
}
