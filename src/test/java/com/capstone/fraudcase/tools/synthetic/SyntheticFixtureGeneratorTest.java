package com.capstone.fraudcase.tools.synthetic;

import com.capstone.fraudcase.domain.ClaimType;
import com.capstone.fraudcase.domain.EvidenceTool;
import com.capstone.fraudcase.support.SyntheticCases;
import com.capstone.fraudcase.tools.ToolException;
import com.capstone.fraudcase.tools.ToolFailure;
import com.capstone.fraudcase.tools.ToolProperties;
import com.capstone.fraudcase.tools.ToolRequest;
import com.capstone.fraudcase.tools.model.DeviceSignals;
import com.capstone.fraudcase.tools.model.LocationComparison;
import com.capstone.fraudcase.tools.model.MerchantRiskProfile;
import com.capstone.fraudcase.tools.model.PriorDisputes;
import com.capstone.fraudcase.tools.model.TransactionDetails;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.List;
import java.util.function.Function;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SyntheticFixtureGeneratorTest {

    private final SyntheticFixtureGenerator fixtures = new SyntheticFixtureGenerator();

    private List<Object> allEvidence(ToolRequest request) {
        return List.of(fixtures.transactionDetails(request), fixtures.deviceSignals(request),
                fixtures.recentLocations(request), fixtures.merchantRiskProfile(request), fixtures.priorDisputes(request));
    }

    @ParameterizedTest
    @ValueSource(strings = {"txn_000123", "txn_fraud_001", "txn_dispute_001", "txn_dup_001", "txn_genuine_001",
            "txn_conflict_001", "txn_partial_001", "REF-abc-998877"})
    void allTools_identicalIdentifiers_produceIdenticalEvidence(String reference) {
        ToolRequest first = SyntheticCases.request(reference, ClaimType.UNRECOGNISED_TRANSACTION);
        ToolRequest second = new ToolRequest("a-completely-different-case", first.customerReference(),
                first.cardReference(), first.claim(), first.caseCreatedAt().plusSeconds(3600),
                first.asOf().plusSeconds(7200));

        assertThat(allEvidence(second)).isEqualTo(allEvidence(first));
    }

    @Test
    void separateGeneratorInstances_identicalIdentifiers_produceIdenticalContentHashes() {
        ToolRequest request = SyntheticCases.request("txn_000777", ClaimType.OTHER);
        SyntheticFixtureGenerator other = new SyntheticFixtureGenerator();

        assertThat(other.transactionDetails(request).contentHash()).isEqualTo(fixtures.transactionDetails(request).contentHash());
        assertThat(other.deviceSignals(request).contentHash()).isEqualTo(fixtures.deviceSignals(request).contentHash());
        assertThat(other.merchantRiskProfile(request).canonicalForm())
                .isEqualTo(fixtures.merchantRiskProfile(request).canonicalForm());
    }

    @Test
    void defaultSeed_isBaselineAndRecordedInSource() {
        assertThat(fixtures.source()).isEqualTo("synthetic-fixture:v1:fraud-training-v1");
    }

    @Test
    void differentGlobalSeed_changesDerivedEvidenceAndSource() {
        SyntheticFixtureGenerator otherSeed = new SyntheticFixtureGenerator(
                new ToolProperties(Duration.ofSeconds(1), "fraud-training-v2"));
        ToolRequest request = SyntheticCases.request("txn_000123", ClaimType.OTHER);

        assertThat(otherSeed.source()).isEqualTo("synthetic-fixture:v1:fraud-training-v2");
        assertThat(otherSeed.deviceSignals(request).deviceToken()).isNotEqualTo(fixtures.deviceSignals(request).deviceToken());
        assertThat(new SyntheticFixtureGenerator(new ToolProperties(Duration.ofSeconds(1), "fraud-training-v2"))
                .deviceSignals(request)).isEqualTo(otherSeed.deviceSignals(request));
    }

    @Test
    void derivedScenario_differentIdentifiers_produceVariedEvidence() {
        List<String> tokens = IntStream.range(0, 20)
                .mapToObj(i -> fixtures.deviceSignals(SyntheticCases.request("txn_var_" + i, ClaimType.OTHER)).deviceToken())
                .distinct().toList();

        assertThat(tokens).hasSize(20);
    }

    @Test
    void derivedScenario_customerScopedFacts_dependOnlyOnCustomer() {
        ToolRequest a = SyntheticCases.request("txn_aaa111", ClaimType.OTHER);
        ToolRequest b = SyntheticCases.request("txn_bbb222", ClaimType.OTHER);

        assertThat(fixtures.priorDisputes(b)).isEqualTo(fixtures.priorDisputes(a));
    }

    @Test
    void fraudScenario_pinsAllFraudSignals() {
        ToolRequest request = SyntheticCases.request("txn_fraud_001", ClaimType.UNRECOGNISED_TRANSACTION);

        TransactionDetails txn = fixtures.transactionDetails(request);
        DeviceSignals device = fixtures.deviceSignals(request);
        LocationComparison location = fixtures.recentLocations(request);
        MerchantRiskProfile merchant = fixtures.merchantRiskProfile(request);

        assertThat(txn.channel()).isEqualTo(TransactionDetails.PaymentChannel.CARD_NOT_PRESENT);
        assertThat(txn.authentication()).isEqualTo(TransactionDetails.AuthenticationMethod.NONE);
        assertThat(device.newDevice()).isTrue();
        assertThat(device.trustedByCustomer()).isFalse();
        assertThat(device.ipRiskScore()).isGreaterThanOrEqualTo(DeviceSignals.HIGH_IP_RISK);
        assertThat(device.firstSeenAt()).isBefore(request.claim().transactionAt());
        assertThat(location.impossibleTravel()).isTrue();
        assertThat(merchant.riskTier()).isEqualTo(MerchantRiskProfile.RiskTier.HIGH);
        assertThat(merchant.recognisedByCustomer()).isFalse();
    }

    @Test
    void transactionAndLocation_reportSameTransactionCountry() {
        ToolRequest request = SyntheticCases.request("txn_conflict_9", ClaimType.UNRECOGNISED_TRANSACTION);

        assertThat(fixtures.recentLocations(request).transactionCountry())
                .isEqualTo(fixtures.transactionDetails(request).merchantCountry());
    }

    @Test
    void transaction_mirrorsClaimAmountAndReference() {
        ToolRequest request = SyntheticCases.request("txn_genuine_1", ClaimType.UNRECOGNISED_TRANSACTION);

        TransactionDetails txn = fixtures.transactionDetails(request);

        assertThat(txn.amount()).isEqualByComparingTo(request.claim().amount());
        assertThat(txn.transactionReference()).isEqualTo("txn_genuine_1");
        assertThat(txn.postedAt()).isAfter(request.claim().transactionAt());
    }

    @Test
    void duplicateScenario_reportsTwoPostingsWithSameFingerprint() {
        TransactionDetails txn = fixtures.transactionDetails(SyntheticCases.request("txn_dup_01", ClaimType.DUPLICATE_CHARGE));

        assertThat(txn.duplicatePosting()).isTrue();
        assertThat(txn.matchingPostings()).isEqualTo(2);
        assertThat(txn.fingerprint()).matches("fp_[a-p]{16}");
    }

    @Test
    void contradictoryScenario_hasFraudAndMitigatingSignals() {
        ToolRequest request = SyntheticCases.request("txn_conflict_1", ClaimType.UNRECOGNISED_TRANSACTION);

        assertThat(fixtures.deviceSignals(request).newDevice()).isTrue();
        assertThat(fixtures.transactionDetails(request).strongAuthentication()).isTrue();
        assertThat(fixtures.merchantRiskProfile(request).recognisedByCustomer()).isTrue();
        assertThat(fixtures.priorDisputes(request)).isEqualTo(new PriorDisputes(3, 0, 1, 2));
    }

    @Test
    void partialHistoryScenario_returnsIncompleteLocationComparison() {
        LocationComparison location = fixtures.recentLocations(
                SyntheticCases.request("txn_partial_1", ClaimType.UNRECOGNISED_TRANSACTION));

        assertThat(location.historyAvailable()).isFalse();
        assertThat(location.lastKnownCountry()).isNull();
        assertThat(location.impossibleTravel()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
            "txn_missing_1, TRANSACTION_DETAILS, NOT_FOUND",
            "txn_timeout_1, MERCHANT_RISK_PROFILE, TIMEOUT",
            "txn_unavail_1, DEVICE_SIGNALS, UNAVAILABLE",
            "txn_unavail_1, RECENT_LOCATIONS, UNAVAILABLE"})
    void failureScenario_injectsStructuredFailureForTool(String reference, EvidenceTool tool, ToolFailure expected) {
        ToolRequest request = SyntheticCases.request(reference, ClaimType.UNRECOGNISED_TRANSACTION);
        Function<ToolRequest, Object> call = switch (tool) {
            case TRANSACTION_DETAILS -> fixtures::transactionDetails;
            case DEVICE_SIGNALS -> fixtures::deviceSignals;
            case RECENT_LOCATIONS -> fixtures::recentLocations;
            case MERCHANT_RISK_PROFILE -> fixtures::merchantRiskProfile;
            default -> fixtures::priorDisputes;
        };

        assertThatThrownBy(() -> call.apply(request)).isInstanceOf(ToolException.class)
                .extracting(e -> ((ToolException) e).failure()).isEqualTo(expected);
    }

    @Test
    void failureScenario_otherToolsStillReturnEvidence() {
        ToolRequest request = SyntheticCases.request("txn_timeout_1", ClaimType.UNRECOGNISED_TRANSACTION);

        assertThat(fixtures.transactionDetails(request)).isNotNull();
        assertThat(fixtures.deviceSignals(request)).isNotNull();
        assertThat(fixtures.priorDisputes(request)).isNotNull();
    }

    @ParameterizedTest
    @CsvSource({
            "txn_fraud_x, FRAUD", "TXN_FRAUD_X, FRAUD", "txn_dispute_x, MERCHANT_DISPUTE", "txn_dup_x, DUPLICATE",
            "txn_missing_x, MISSING_TRANSACTION", "txn_conflict_x, CONTRADICTORY", "txn_genuine_x, GENUINE",
            "txn_timeout_x, TOOL_TIMEOUT", "txn_unavail_x, TOOLS_UNAVAILABLE", "txn_partial_x, PARTIAL_HISTORY",
            "txn_000123, DERIVED", "fraud_txn_1, DERIVED"})
    void forReference_prefixSelectsScenario(String reference, SyntheticScenario expected) {
        assertThat(SyntheticScenario.forReference(reference)).isEqualTo(expected);
    }

    @ParameterizedTest
    @EnumSource(value = SyntheticScenario.class, names = {"FRAUD", "MERCHANT_DISPUTE", "DUPLICATE", "CONTRADICTORY",
            "GENUINE", "PARTIAL_HISTORY", "DERIVED"})
    void nonFailingScenarios_injectNoFailure(SyntheticScenario scenario) {
        assertThat(EvidenceTool.values()).allSatisfy(tool -> assertThat(scenario.failureFor(tool)).isEmpty());
    }
}
