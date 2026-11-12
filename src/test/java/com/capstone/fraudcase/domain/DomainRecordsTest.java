package com.capstone.fraudcase.domain;

import com.capstone.fraudcase.TestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DomainRecordsTest {

    private static final Instant NOW = Instant.parse("2026-11-01T10:00:00Z");

    @ParameterizedTest
    @ValueSource(strings = {"0", "-0.01", "-100"})
    void transactionClaim_nonPositiveAmount_throws(String amount) {
        assertThatThrownBy(() -> new TransactionClaim("txn_1", "Merchant", null, new BigDecimal(amount),
                Currency.getInstance("GBP"), NOW, ClaimType.OTHER))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("positive");
    }

    @ParameterizedTest
    @CsvSource({"GBP,10.001", "JPY,100.5", "USD,0.333"})
    void transactionClaim_tooManyDecimalsForCurrency_throws(String currency, String amount) {
        assertThatThrownBy(() -> new TransactionClaim("txn_1", "Merchant", null, new BigDecimal(amount),
                Currency.getInstance(currency), NOW, ClaimType.OTHER))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining(currency);
    }

    @ParameterizedTest
    @CsvSource({"GBP,10.50", "JPY,100", "JPY,100.00", "USD,0.01"})
    void transactionClaim_validPrecision_accepted(String currency, String amount) {
        assertThat(new TransactionClaim("txn_1", "Merchant", null, new BigDecimal(amount),
                Currency.getInstance(currency), NOW, ClaimType.OTHER).amount()).isEqualByComparingTo(amount);
    }

    @Test
    void transactionClaim_missingTransactionReference_throws() {
        assertThatThrownBy(() -> new TransactionClaim(" ", "Merchant", null, BigDecimal.TEN,
                Currency.getInstance("GBP"), NOW, ClaimType.OTHER))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("transactionReference");
    }

    @Test
    void evidenceItem_toolResultWithoutTrace_throws() {
        assertThatThrownBy(() -> new EvidenceItem("ev-1", "case-1", EvidenceTool.DEVICE_SIGNALS,
                ProvenanceType.TOOL_RESULT, EvidenceStatus.AVAILABLE, "New device seen", null, NOW))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("tool trace");
    }

    @Test
    void evidenceItem_unavailableToolResultWithTrace_isRepresentedNotAvailable() {
        EvidenceItem item = new EvidenceItem("ev-1", "case-1", EvidenceTool.DEVICE_SIGNALS,
                ProvenanceType.TOOL_RESULT, EvidenceStatus.TIMEOUT, "Device signals timed out", "trace-1", NOW);

        assertThat(item.isAvailable()).isFalse();
    }

    @Test
    void evidenceItem_customerStatementWithoutTool_accepted() {
        EvidenceItem item = new EvidenceItem("ev-1", "case-1", null, ProvenanceType.CUSTOMER_STATEMENT,
                EvidenceStatus.AVAILABLE, "Customer says card was in possession", null, NOW);

        assertThat(item.isAvailable()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.01, 1.01, Double.NaN})
    void policyCitation_scoreOutsideRange_throws(double score) {
        assertThatThrownBy(() -> new PolicyCitation("POL-1", "Title", "1.0", LocalDate.of(2026, 1, 1), "1.1",
                "owner-1", "POL-1#c1", score, "excerpt"))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("relevanceScore");
    }

    @Test
    void policyCitation_missingVersion_throws() {
        assertThatThrownBy(() -> new PolicyCitation("POL-1", "Title", null, LocalDate.of(2026, 1, 1), "1.1",
                "owner-1", "POL-1#c1", 0.5, "excerpt"))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("documentVersion");
    }

    @Test
    void policyCitation_fixture_preservesMetadata() {
        PolicyCitation citation = TestFixtures.policyCitation();

        assertThat(citation.documentId()).isEqualTo("POL-FRAUD-001");
        assertThat(citation.chunkId()).isEqualTo("POL-FRAUD-001#c12");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "too short", "         x"})
    void reviewDisposition_missingOrShortRationale_throws(String rationale) {
        assertThatThrownBy(() -> new ReviewDisposition("case-1", "rec-1", "analyst-1", ReviewDecision.ACCEPT,
                null, null, rationale, NOW))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("rationale");
    }

    @Test
    void reviewDisposition_overrideWithoutFinalClassificationOrRoute_throws() {
        assertThatThrownBy(() -> new ReviewDisposition("case-1", "rec-1", "analyst-1", ReviewDecision.OVERRIDE,
                Classification.MERCHANT_DISPUTE, null, "Merchant confirmed the order was cancelled.", NOW))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("override");
    }

    @Test
    void reviewDisposition_acceptWithoutRecommendation_throws() {
        assertThatThrownBy(() -> new ReviewDisposition("case-1", null, "analyst-1", ReviewDecision.ACCEPT,
                null, null, "Agree with the fraud recommendation.", NOW))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("recommendationId");
    }

    @ParameterizedTest
    @CsvSource({"ACCEPT,ROUTED", "OVERRIDE,ROUTED", "CLOSE,CLOSED"})
    void reviewDisposition_resultingStatus_followsDecision(ReviewDecision decision, CaseStatus expected) {
        ReviewDisposition disposition = new ReviewDisposition("case-1", "rec-1", "analyst-1", decision,
                Classification.MERCHANT_DISPUTE, Route.DISPUTE_OPERATIONS, "Reviewed all evidence items.", NOW);

        assertThat(disposition.resultingStatus()).isEqualTo(expected);
    }

    @Test
    void reviewDisposition_closeWithoutRecommendation_accepted() {
        ReviewDisposition disposition = new ReviewDisposition("case-1", null, "analyst-1", ReviewDecision.CLOSE,
                null, null, "Customer withdrew the claim by phone.", NOW);

        assertThat(disposition.resultingStatus()).isEqualTo(CaseStatus.CLOSED);
    }

    @Test
    void evidenceTool_allowList_exposesRequiredToolIds() {
        assertThat(EvidenceTool.values()).extracting(EvidenceTool::toolId).contains("getTransactionDetails",
                "getDeviceSignals", "compareRecentLocations", "getMerchantRiskProfile", "getPriorDisputes",
                "calculateCaseSla");
    }
}
