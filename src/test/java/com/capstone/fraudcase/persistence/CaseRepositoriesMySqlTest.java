package com.capstone.fraudcase.persistence;

import com.capstone.fraudcase.TestFixtures;
import com.capstone.fraudcase.domain.CaseStatus;
import com.capstone.fraudcase.domain.Classification;
import com.capstone.fraudcase.domain.EvidenceStatus;
import com.capstone.fraudcase.domain.EvidenceTool;
import com.capstone.fraudcase.domain.FraudCaseRecommendation;
import com.capstone.fraudcase.domain.PolicyCitation;
import com.capstone.fraudcase.domain.ProvenanceType;
import com.capstone.fraudcase.domain.ReviewDecision;
import com.capstone.fraudcase.domain.ReviewDisposition;
import com.capstone.fraudcase.domain.Route;
import com.capstone.fraudcase.domain.ToolTraceStatus;
import com.capstone.fraudcase.domain.TransactionClaim;
import com.capstone.fraudcase.persistence.entity.EvidenceItemEntity;
import com.capstone.fraudcase.persistence.entity.FraudCaseEntity;
import com.capstone.fraudcase.persistence.entity.PolicyCitationEntity;
import com.capstone.fraudcase.persistence.entity.RecommendationEntity;
import com.capstone.fraudcase.persistence.entity.ReviewDispositionEntity;
import com.capstone.fraudcase.persistence.entity.ToolTraceEntity;
import com.capstone.fraudcase.persistence.entity.TransactionClaimEntity;
import com.capstone.fraudcase.persistence.repository.EvidenceItemRepository;
import com.capstone.fraudcase.persistence.repository.FraudCaseRepository;
import com.capstone.fraudcase.persistence.repository.PolicyCitationRepository;
import com.capstone.fraudcase.persistence.repository.RecommendationRepository;
import com.capstone.fraudcase.persistence.repository.ReviewDispositionRepository;
import com.capstone.fraudcase.persistence.repository.ToolTraceRepository;
import com.capstone.fraudcase.persistence.repository.TransactionClaimRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@MySqlRepositoryTest
class CaseRepositoriesMySqlTest {

    @Autowired
    private FraudCaseRepository cases;
    @Autowired
    private TransactionClaimRepository claims;
    @Autowired
    private ToolTraceRepository traces;
    @Autowired
    private EvidenceItemRepository evidence;
    @Autowired
    private RecommendationRepository recommendations;
    @Autowired
    private PolicyCitationRepository citations;
    @Autowired
    private ReviewDispositionRepository reviews;
    @Autowired
    private EntityManager em;
    @Autowired
    private JdbcTemplate jdbc;

    private FraudCaseEntity persistCase(String analyst) {
        FraudCaseEntity entity = cases.saveAndFlush(new FraudCaseEntity("cus_A1b2C3d4", "****4242",
                "I do not recognise this purchase.", analyst));
        em.clear();
        return entity;
    }

    private EvidenceItemEntity persistToolEvidence(String caseId) {
        ToolTraceEntity trace = new ToolTraceEntity(caseId, EvidenceTool.DEVICE_SIGNALS, "device lookup", "corr-1",
                Instant.now());
        trace.complete(ToolTraceStatus.SUCCESS, "new device", null, Instant.now());
        traces.saveAndFlush(trace);
        return evidence.saveAndFlush(new EvidenceItemEntity(caseId, EvidenceTool.DEVICE_SIGNALS,
                ProvenanceType.TOOL_RESULT, EvidenceStatus.AVAILABLE, "Device first seen 5 minutes before purchase",
                trace.getId(), Instant.now()));
    }

    @Test
    void fraudCase_saveAndReload_roundTripsAllFields() {
        FraudCaseEntity saved = persistCase("analyst-roundtrip");

        FraudCaseEntity loaded = cases.findById(saved.getId()).orElseThrow();

        assertThat(loaded.getStatus()).isEqualTo(CaseStatus.NEW);
        assertThat(loaded.getCustomerReference()).isEqualTo("cus_A1b2C3d4");
        assertThat(loaded.getCardReference()).isEqualTo("****4242");
        assertThat(loaded.isHumanReviewRequired()).isTrue();
        assertThat(loaded.getCreatedAt()).isEqualTo(saved.getCreatedAt());
        assertThat(loaded.isNew()).isFalse();
    }

    @Test
    void fraudCase_status_storedAsReadableString() {
        FraudCaseEntity saved = persistCase("analyst-enum");

        String raw = jdbc.queryForObject("SELECT status FROM fraud_case WHERE id = ?", String.class, saved.getId());

        assertThat(raw).isEqualTo("NEW");
    }

    @Test
    void fraudCase_transitionPersisted_incrementsVersionAndUpdatedAt() {
        FraudCaseEntity saved = persistCase("analyst-transition");
        FraudCaseEntity loaded = cases.findById(saved.getId()).orElseThrow();

        loaded.transitionTo(CaseStatus.ENRICHING);
        cases.saveAndFlush(loaded);
        em.clear();

        FraudCaseEntity reloaded = cases.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(CaseStatus.ENRICHING);
        assertThat(reloaded.getVersion()).isEqualTo(saved.getVersion() + 1);
        assertThat(reloaded.getUpdatedAt()).isAfterOrEqualTo(saved.getUpdatedAt());
    }

    @Test
    void fraudCase_findByStatusAndCreator_returnsOnlyMatching() {
        FraudCaseEntity first = persistCase("analyst-query");
        FraudCaseEntity second = persistCase("analyst-query");
        FraudCaseEntity moved = cases.findById(second.getId()).orElseThrow();
        moved.transitionTo(CaseStatus.ENRICHING);
        cases.saveAndFlush(moved);

        assertThat(cases.findByCreatedByOrderByCreatedAtDesc("analyst-query")).hasSize(2);
        assertThat(cases.findByStatusOrderByCreatedAtAsc(CaseStatus.ENRICHING)).extracting(FraudCaseEntity::getId)
                .contains(second.getId()).doesNotContain(first.getId());
    }

    @Test
    void transactionClaim_decimalAmountAndTimestamp_roundTripExactly() {
        FraudCaseEntity fraudCase = persistCase("analyst-claim");
        claims.saveAndFlush(new TransactionClaimEntity(fraudCase.getId(), TestFixtures.transactionClaim()));
        em.clear();

        TransactionClaim loaded = claims.findByCaseId(fraudCase.getId()).orElseThrow().toDomain();

        assertThat(loaded.amount()).isEqualTo(new BigDecimal("249.99"));
        assertThat(loaded.currency().getCurrencyCode()).isEqualTo("GBP");
        assertThat(loaded.transactionAt()).isEqualTo(TestFixtures.TRANSACTION_AT);
        assertThat(loaded).isEqualTo(TestFixtures.transactionClaim());
    }

    @Test
    void transactionClaim_secondClaimForSameCase_rejected() {
        FraudCaseEntity fraudCase = persistCase("analyst-claim-dup");
        claims.saveAndFlush(new TransactionClaimEntity(fraudCase.getId(), TestFixtures.transactionClaim()));

        assertThatThrownBy(() -> claims.saveAndFlush(
                new TransactionClaimEntity(fraudCase.getId(), TestFixtures.transactionClaim())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void transactionClaim_unknownCase_rejectedByForeignKey() {
        assertThatThrownBy(() -> claims.saveAndFlush(
                new TransactionClaimEntity("00000000-0000-0000-0000-000000000000", TestFixtures.transactionClaim())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void evidence_caseScopedLookup_neverReturnsAnotherCasesEvidence() {
        FraudCaseEntity caseA = persistCase("analyst-isolation");
        FraudCaseEntity caseB = persistCase("analyst-isolation");
        EvidenceItemEntity evidenceA = persistToolEvidence(caseA.getId());
        EvidenceItemEntity evidenceB = persistToolEvidence(caseB.getId());

        assertThat(evidence.findByCaseIdAndIdIn(caseB.getId(), List.of(evidenceA.getId(), evidenceB.getId())))
                .extracting(EvidenceItemEntity::getId).containsExactly(evidenceB.getId());
        assertThat(evidence.findByCaseIdOrderByCollectedAtAsc(caseA.getId()))
                .extracting(EvidenceItemEntity::getCaseId).containsOnly(caseA.getId());
    }

    @Test
    void toolTrace_unavailableEvidence_persistedWithStructuredStatus() {
        FraudCaseEntity fraudCase = persistCase("analyst-timeout");
        Instant start = Instant.now();
        ToolTraceEntity trace = new ToolTraceEntity(fraudCase.getId(), EvidenceTool.MERCHANT_RISK_PROFILE,
                "merchant=M1", "corr-2", start);
        trace.complete(ToolTraceStatus.TIMEOUT, null, "TOOL_TIMEOUT", start.plusMillis(2000));
        traces.saveAndFlush(trace);
        evidence.saveAndFlush(new EvidenceItemEntity(fraudCase.getId(), EvidenceTool.MERCHANT_RISK_PROFILE,
                ProvenanceType.TOOL_RESULT, EvidenceStatus.TIMEOUT, "Merchant risk profile unavailable (timeout)",
                trace.getId(), Instant.now()));
        em.clear();

        ToolTraceEntity loadedTrace = traces.findByCaseIdOrderByStartedAtAsc(fraudCase.getId()).getFirst();
        assertThat(loadedTrace.getStatus()).isEqualTo(ToolTraceStatus.TIMEOUT);
        assertThat(loadedTrace.getDurationMs()).isEqualTo(2000L);
        assertThat(evidence.findByCaseIdOrderByCollectedAtAsc(fraudCase.getId()).getFirst().toDomain().isAvailable())
                .isFalse();
    }

    @Test
    void recommendation_withCollectionsAndCitations_roundTripsToValidDomain() {
        FraudCaseEntity fraudCase = persistCase("analyst-rec");
        EvidenceItemEntity ev = persistToolEvidence(fraudCase.getId());
        FraudCaseRecommendation domain = TestFixtures.recommendation(fraudCase.getId(), List.of(ev.getId()));
        RecommendationEntity saved = recommendations.saveAndFlush(new RecommendationEntity(domain));
        citations.saveAndFlush(new PolicyCitationEntity(saved.getId(), fraudCase.getId(), domain.policyCitations().getFirst()));
        em.clear();

        RecommendationEntity loaded = recommendations.findFirstByCaseIdOrderByCreatedAtDesc(fraudCase.getId()).orElseThrow();
        List<PolicyCitation> loadedCitations = citations.findByRecommendationIdOrderByRelevanceScoreDesc(loaded.getId())
                .stream().map(PolicyCitationEntity::toDomain).toList();
        FraudCaseRecommendation roundTripped = loaded.toDomain(loadedCitations);

        assertThat(roundTripped.confidence()).isEqualTo(new BigDecimal("0.820"));
        assertThat(roundTripped.evidenceIds()).containsExactly(ev.getId());
        assertThat(roundTripped.traceIds()).containsExactly("rule:new-device", "trace-1");
        assertThat(roundTripped.policyCitations()).containsExactly(TestFixtures.policyCitation());
        assertThat(roundTripped.humanReviewRequired()).isTrue();
    }

    @Test
    void recommendation_insufficientEvidence_persistsMissingInformation() {
        FraudCaseEntity fraudCase = persistCase("analyst-insufficient");
        FraudCaseRecommendation domain = new FraudCaseRecommendation(fraudCase.getId(),
                Classification.INSUFFICIENT_EVIDENCE, com.capstone.fraudcase.domain.RiskLevel.MEDIUM,
                Route.MANUAL_REVIEW, new BigDecimal("0.100"), true, List.of(), List.of(),
                List.of("transaction details", "merchant response"), List.of(), "Cannot classify yet.",
                com.capstone.fraudcase.domain.ProcessorMode.RULES);
        RecommendationEntity saved = recommendations.saveAndFlush(new RecommendationEntity(domain));
        em.clear();

        FraudCaseRecommendation loaded = recommendations.findById(saved.getId()).orElseThrow().toDomain(List.of());

        assertThat(loaded.missingInformation()).containsExactly("transaction details", "merchant response");
        assertThat(loaded.evidenceIds()).isEmpty();
    }

    @Test
    void recommendation_referencingUnknownEvidence_rejectedByForeignKey() {
        FraudCaseEntity fraudCase = persistCase("analyst-rec-fk");
        FraudCaseRecommendation domain = TestFixtures.recommendation(fraudCase.getId(), List.of("no-such-evidence"));

        assertThatThrownBy(() -> recommendations.saveAndFlush(new RecommendationEntity(domain)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void recommendation_lookupByIdAndOtherCase_returnsEmpty() {
        FraudCaseEntity caseA = persistCase("analyst-rec-scope");
        FraudCaseEntity caseB = persistCase("analyst-rec-scope");
        EvidenceItemEntity ev = persistToolEvidence(caseA.getId());
        RecommendationEntity recA = recommendations.saveAndFlush(
                new RecommendationEntity(TestFixtures.recommendation(caseA.getId(), List.of(ev.getId()))));

        assertThat(recommendations.findByIdAndCaseId(recA.getId(), caseB.getId())).isEmpty();
        assertThat(recommendations.findByIdAndCaseId(recA.getId(), caseA.getId())).isPresent();
    }

    @Test
    void reviewDisposition_override_roundTripsReviewerRationale() {
        FraudCaseEntity fraudCase = persistCase("analyst-review");
        EvidenceItemEntity ev = persistToolEvidence(fraudCase.getId());
        RecommendationEntity rec = recommendations.saveAndFlush(
                new RecommendationEntity(TestFixtures.recommendation(fraudCase.getId(), List.of(ev.getId()))));
        ReviewDisposition disposition = new ReviewDisposition(fraudCase.getId(), rec.getId(), "analyst-review",
                ReviewDecision.OVERRIDE, Classification.MERCHANT_DISPUTE, Route.DISPUTE_OPERATIONS,
                "Merchant confirmed cancellation; not fraud.", TestFixtures.TRANSACTION_AT);
        reviews.saveAndFlush(new ReviewDispositionEntity(disposition));
        em.clear();

        assertThat(reviews.findByCaseIdOrderByReviewedAtAsc(fraudCase.getId()))
                .extracting(ReviewDispositionEntity::toDomain).containsExactly(disposition);
    }
}
