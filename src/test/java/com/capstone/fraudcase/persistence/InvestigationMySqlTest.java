package com.capstone.fraudcase.persistence;

import com.capstone.fraudcase.agent.common.RecommendationValidator;
import com.capstone.fraudcase.agent.rules.RulesFraudCaseAgent;
import com.capstone.fraudcase.application.InvestigationOrchestrator;
import com.capstone.fraudcase.application.InvestigationResult;
import com.capstone.fraudcase.domain.AuditEventType;
import com.capstone.fraudcase.domain.CaseStatus;
import com.capstone.fraudcase.domain.ClaimType;
import com.capstone.fraudcase.domain.Classification;
import com.capstone.fraudcase.domain.EvidenceStatus;
import com.capstone.fraudcase.domain.EvidenceTool;
import com.capstone.fraudcase.domain.FraudCaseRecommendation;
import com.capstone.fraudcase.domain.ProcessorMode;
import com.capstone.fraudcase.domain.ProvenanceType;
import com.capstone.fraudcase.domain.ToolTraceStatus;
import com.capstone.fraudcase.knowledge.PolicyRetriever;
import com.capstone.fraudcase.persistence.audit.AuditTrail;
import com.capstone.fraudcase.persistence.entity.AuditEventEntity;
import com.capstone.fraudcase.persistence.entity.EvidenceItemEntity;
import com.capstone.fraudcase.persistence.entity.FraudCaseEntity;
import com.capstone.fraudcase.persistence.entity.RecommendationEntity;
import com.capstone.fraudcase.persistence.entity.ToolTraceEntity;
import com.capstone.fraudcase.persistence.entity.TransactionClaimEntity;
import com.capstone.fraudcase.persistence.repository.EvidenceItemRepository;
import com.capstone.fraudcase.persistence.repository.FraudCaseRepository;
import com.capstone.fraudcase.persistence.repository.RecommendationRepository;
import com.capstone.fraudcase.persistence.repository.ToolTraceRepository;
import com.capstone.fraudcase.persistence.repository.TransactionClaimRepository;
import com.capstone.fraudcase.support.SyntheticCases;
import com.capstone.fraudcase.tools.ToolInvoker;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** End-to-end RULES-mode investigation against real MySQL: no model, every artefact persisted. */
@MySqlRepositoryTest
@Import(InvestigationMySqlTest.InvestigationComponents.class)
class InvestigationMySqlTest {

    @TestConfiguration(proxyBeanMethods = false)
    @ComponentScan(basePackageClasses = {InvestigationOrchestrator.class, ToolInvoker.class,
            RulesFraudCaseAgent.class, RecommendationValidator.class, AuditTrail.class, PolicyRetriever.class})
    static class InvestigationComponents {
    }

    @Autowired
    private InvestigationOrchestrator orchestrator;
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
    private AuditTrail audit;
    @Autowired
    private EntityManager em;
    @Autowired
    private JdbcTemplate jdbc;

    private String newCase(String reference, ClaimType claimType) {
        FraudCaseEntity fraudCase = cases.saveAndFlush(new FraudCaseEntity(SyntheticCases.CUSTOMER,
                SyntheticCases.CARD, "I do not recognise this purchase.", "analyst-it"));
        claims.saveAndFlush(new TransactionClaimEntity(fraudCase.getId(),
                SyntheticCases.claim(reference, claimType, new BigDecimal("249.99"))));
        em.clear();
        return fraudCase.getId();
    }

    private InvestigationResult investigate(String caseId) {
        InvestigationResult result = orchestrator.investigate(caseId, "analyst-it", "corr-it");
        em.flush();
        em.clear();
        return result;
    }

    private Map<EvidenceTool, EvidenceItemEntity> evidenceByTool(String caseId) {
        return evidence.findByCaseIdOrderByCollectedAtAsc(caseId).stream()
                .collect(Collectors.toMap(EvidenceItemEntity::getTool, Function.identity()));
    }

    @Test
    void investigate_rulesMode_persistsTracesEvidenceRecommendationAndAudit() {
        String caseId = newCase("txn_fraud_001", ClaimType.UNRECOGNISED_TRANSACTION);

        InvestigationResult result = investigate(caseId);

        FraudCaseEntity reloaded = cases.findById(caseId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(CaseStatus.READY_FOR_REVIEW);
        assertThat(reloaded.getVersion()).isEqualTo(1L);

        List<ToolTraceEntity> savedTraces = traces.findByCaseIdOrderByStartedAtAsc(caseId);
        assertThat(savedTraces).hasSize(6).allSatisfy(t -> {
            assertThat(t.getStatus()).isEqualTo(ToolTraceStatus.SUCCESS);
            assertThat(t.getCorrelationId()).isEqualTo("corr-it");
            assertThat(t.getCompletedAt()).isNotNull();
            assertThat(t.getDurationMs()).isNotNegative();
            assertThat(t.getRequestSummary()).contains("txn_fraud_001").doesNotContain(SyntheticCases.CUSTOMER);
        });
        assertThat(savedTraces).extracting(ToolTraceEntity::getTool)
                .containsExactlyInAnyOrderElementsOf(InvestigationOrchestrator.EVIDENCE_PLAN);

        List<EvidenceItemEntity> items = evidence.findByCaseIdOrderByCollectedAtAsc(caseId);
        assertThat(items).hasSize(6).allSatisfy(e -> {
            assertThat(e.toDomain().provenance()).isEqualTo(ProvenanceType.TOOL_RESULT);
            assertThat(e.getStatus()).isEqualTo(EvidenceStatus.AVAILABLE);
            assertThat(e.getToolTraceId()).isIn(savedTraces.stream().map(ToolTraceEntity::getId).toList());
            assertThat(e.getSource()).isNotBlank();
            assertThat(e.getContentHash()).hasSize(64);
            assertThat(e.getPayload()).isNotBlank();
        });

        RecommendationEntity rec = recommendations.findFirstByCaseIdOrderByCreatedAtDesc(caseId).orElseThrow();
        FraudCaseRecommendation domain = rec.toDomain(List.of());
        assertThat(rec.getId()).isEqualTo(result.recommendationId());
        assertThat(domain.classification()).isEqualTo(Classification.UNAUTHORISED_TRANSACTION);
        assertThat(domain.processorMode()).isEqualTo(ProcessorMode.RULES);
        assertThat(domain.humanReviewRequired()).isTrue();
        assertThat(domain.evidenceIds()).containsExactlyInAnyOrderElementsOf(items.stream().map(EvidenceItemEntity::getId).toList());
        assertThat(domain.traceIds()).contains("rule:R-FRAUD-01")
                .containsAll(savedTraces.stream().map(ToolTraceEntity::getId).toList());

        Map<AuditEventType, Long> auditCounts = audit.history(caseId).stream()
                .collect(Collectors.groupingBy(AuditEventEntity::getEventType, Collectors.counting()));
        assertThat(auditCounts).containsExactlyInAnyOrderEntriesOf(Map.of(
                AuditEventType.STATUS_CHANGED, 2L, AuditEventType.TOOL_INVOKED, 6L,
                AuditEventType.EVIDENCE_RECORDED, 6L, AuditEventType.RECOMMENDATION_RECORDED, 1L,
                AuditEventType.POLICY_RETRIEVED, 1L));
    }

    @ParameterizedTest(name = "{0} -> {2} / {3}")
    @CsvSource({
            "txn_fraud_101,    UNRECOGNISED_TRANSACTION, UNAUTHORISED_TRANSACTION, READY_FOR_REVIEW",
            "txn_dispute_101,  MERCHANT_ISSUE,           MERCHANT_DISPUTE,         READY_FOR_REVIEW",
            "txn_dup_101,      DUPLICATE_CHARGE,         DUPLICATE_PROCESSING,     READY_FOR_REVIEW",
            "txn_missing_101,  UNRECOGNISED_TRANSACTION, INSUFFICIENT_EVIDENCE,    INCOMPLETE",
            "txn_conflict_101, UNRECOGNISED_TRANSACTION, SUSPICIOUS_TRANSACTION,   READY_FOR_REVIEW",
            "txn_genuine_101,  UNRECOGNISED_TRANSACTION, GENUINE_TRANSACTION,      READY_FOR_REVIEW",
            "txn_timeout_101,  UNRECOGNISED_TRANSACTION, UNAUTHORISED_TRANSACTION, INCOMPLETE",
            "txn_unavail_101,  UNRECOGNISED_TRANSACTION, INSUFFICIENT_EVIDENCE,    INCOMPLETE",
            "txn_partial_101,  UNRECOGNISED_TRANSACTION, GENUINE_TRANSACTION,      READY_FOR_REVIEW"})
    void investigate_syntheticScenario_persistsDeterministicOutcome(String reference, ClaimType claimType,
                                                                    Classification classification, CaseStatus status) {
        String caseId = newCase(reference, claimType);

        investigate(caseId);

        assertThat(cases.findById(caseId).orElseThrow().getStatus()).isEqualTo(status);
        assertThat(recommendations.findFirstByCaseIdOrderByCreatedAtDesc(caseId).orElseThrow().getClassification())
                .isEqualTo(classification);
    }

    @Test
    void investigate_identicalIdentifiersInTwoCases_produceIdenticalEvidence() {
        String first = newCase("txn_000555", ClaimType.UNRECOGNISED_TRANSACTION);
        String second = newCase("txn_000555", ClaimType.UNRECOGNISED_TRANSACTION);

        InvestigationResult a = investigate(first);
        InvestigationResult b = investigate(second);

        Map<EvidenceTool, EvidenceItemEntity> evidenceA = evidenceByTool(first);
        Map<EvidenceTool, EvidenceItemEntity> evidenceB = evidenceByTool(second);
        for (EvidenceTool tool : InvestigationOrchestrator.EVIDENCE_PLAN) {
            if (tool == EvidenceTool.CASE_SLA) {
                continue;
            }
            assertThat(evidenceB.get(tool).getContentHash()).as(tool.toolId()).isEqualTo(evidenceA.get(tool).getContentHash());
            assertThat(evidenceB.get(tool).getSummary()).as(tool.toolId()).isEqualTo(evidenceA.get(tool).getSummary());
            assertThat(evidenceB.get(tool).getId()).isNotEqualTo(evidenceA.get(tool).getId());
        }
        assertThat(b.recommendation().classification()).isEqualTo(a.recommendation().classification());
        assertThat(b.recommendation().route()).isEqualTo(a.recommendation().route());
        assertThat(b.recommendation().riskLevel()).isEqualTo(a.recommendation().riskLevel());
        assertThat(b.recommendation().confidence()).isEqualTo(a.recommendation().confidence());
    }

    @Test
    void t08_toolTimeout_persistedAsStructuredUnavailableEvidence() {
        String caseId = newCase("txn_timeout_102", ClaimType.UNRECOGNISED_TRANSACTION);

        investigate(caseId);

        ToolTraceEntity trace = traces.findByCaseIdOrderByStartedAtAsc(caseId).stream()
                .filter(t -> t.getTool() == EvidenceTool.MERCHANT_RISK_PROFILE).findFirst().orElseThrow();
        assertThat(trace.getStatus()).isEqualTo(ToolTraceStatus.TIMEOUT);
        assertThat(trace.getErrorCode()).isEqualTo("TOOL_TIMEOUT");
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT status, content_hash, payload, summary FROM evidence_item WHERE tool_trace_id = ?", trace.getId());
        assertThat(row.get("status")).isEqualTo("TIMEOUT");
        assertThat(row.get("content_hash")).isNull();
        assertThat(row.get("payload")).isNull();
        assertThat((String) row.get("summary")).contains("No data was inferred");
        assertThat(recommendations.findFirstByCaseIdOrderByCreatedAtDesc(caseId).orElseThrow().toDomain(List.of())
                .missingInformation()).contains("getMerchantRiskProfile returned no evidence (TIMEOUT)");
    }

    @Test
    void investigate_incompleteCaseReEnriched_appendsNewArtefactsAndKeepsAuditHistory() {
        String caseId = newCase("txn_unavail_102", ClaimType.UNRECOGNISED_TRANSACTION);
        investigate(caseId);
        List<AuditEventEntity> before = audit.history(caseId);

        investigate(caseId);

        List<AuditEventEntity> after = audit.history(caseId);
        assertThat(cases.findById(caseId).orElseThrow().getStatus()).isEqualTo(CaseStatus.INCOMPLETE);
        assertThat(after).hasSize(before.size() * 2);
        assertThat(after).extracting(AuditEventEntity::getId)
                .containsAll(before.stream().map(AuditEventEntity::getId).toList());
        assertThat(after).filteredOn(e -> before.stream().anyMatch(b -> b.getId().equals(e.getId())))
                .extracting(AuditEventEntity::getDetails)
                .containsExactlyInAnyOrderElementsOf(before.stream().map(AuditEventEntity::getDetails).toList());
        assertThat(recommendations.findByCaseIdOrderByCreatedAtDesc(caseId)).hasSize(2);
        assertThat(evidence.findByCaseIdOrderByCollectedAtAsc(caseId)).hasSize(12);
        assertThat(after).anySatisfy(e -> {
            assertThat(e.getFromStatus()).isEqualTo(CaseStatus.INCOMPLETE);
            assertThat(e.getToStatus()).isEqualTo(CaseStatus.ENRICHING);
        });
    }

    @Test
    void t04_caseWithoutClaim_incompleteRecommendationWithoutEvidence() {
        String caseId = cases.saveAndFlush(new FraudCaseEntity(SyntheticCases.CUSTOMER, null, "No details.",
                "analyst-it")).getId();
        em.clear();

        InvestigationResult result = investigate(caseId);

        assertThat(result.status()).isEqualTo(CaseStatus.INCOMPLETE);
        assertThat(traces.findByCaseIdOrderByStartedAtAsc(caseId)).isEmpty();
        assertThat(recommendations.findFirstByCaseIdOrderByCreatedAtDesc(caseId).orElseThrow().toDomain(List.of())
                .evidenceIds()).isEmpty();
    }

    @Test
    void t10_evidenceAndRecommendations_isolatedPerCase() {
        String caseA = newCase("txn_fraud_103", ClaimType.UNRECOGNISED_TRANSACTION);
        String caseB = newCase("txn_genuine_103", ClaimType.UNRECOGNISED_TRANSACTION);
        investigate(caseA);
        investigate(caseB);

        List<String> evidenceA = evidence.findByCaseIdOrderByCollectedAtAsc(caseA).stream().map(EvidenceItemEntity::getId).toList();
        FraudCaseRecommendation recB = recommendations.findFirstByCaseIdOrderByCreatedAtDesc(caseB).orElseThrow()
                .toDomain(List.of());

        assertThat(recB.evidenceIds()).doesNotContainAnyElementsOf(evidenceA);
        assertThat(evidence.findByCaseIdAndIdIn(caseB, evidenceA)).isEmpty();
        assertThat(audit.history(caseB)).extracting(AuditEventEntity::getCaseId).containsOnly(caseB);
    }

    @Test
    void v2CheckConstraint_payloadOnUnavailableEvidence_rejectedByDatabase() {
        String caseId = newCase("txn_timeout_104", ClaimType.UNRECOGNISED_TRANSACTION);
        investigate(caseId);
        String traceId = traces.findByCaseIdOrderByStartedAtAsc(caseId).getFirst().getId();

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO evidence_item (id, case_id, tool_trace_id, tool, provenance, status, summary, collected_at,
                                           source, content_hash, payload)
                VALUES ('ev-bad', ?, ?, 'DEVICE_SIGNALS', 'TOOL_RESULT', 'TIMEOUT', 's', NOW(6), 'x:v1', REPEAT('a', 64), 'p')""",
                caseId, traceId)).isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_evidence_item_payload_available");
    }
}
