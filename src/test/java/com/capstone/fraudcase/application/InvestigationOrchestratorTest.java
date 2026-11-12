package com.capstone.fraudcase.application;

import com.capstone.fraudcase.TestFixtures;
import com.capstone.fraudcase.agent.common.FraudCaseContext;
import com.capstone.fraudcase.agent.common.FraudCaseProcessor;
import com.capstone.fraudcase.agent.common.RecommendationValidator;
import com.capstone.fraudcase.agent.rules.RulesCaseAssistant;
import com.capstone.fraudcase.agent.rules.RulesFraudCaseAgent;
import com.capstone.fraudcase.domain.AuditEventType;
import com.capstone.fraudcase.domain.CaseStatus;
import com.capstone.fraudcase.domain.ClaimType;
import com.capstone.fraudcase.domain.Classification;
import com.capstone.fraudcase.domain.InvalidCaseTransitionException;
import com.capstone.fraudcase.domain.Route;
import com.capstone.fraudcase.knowledge.LexicalHashEmbeddingModel;
import com.capstone.fraudcase.knowledge.PolicyProperties;
import com.capstone.fraudcase.knowledge.PolicyRetriever;
import com.capstone.fraudcase.memory.CaseConversationMemory;
import com.capstone.fraudcase.memory.MemoryProperties;
import com.capstone.fraudcase.persistence.audit.AuditTrail;
import com.capstone.fraudcase.persistence.entity.EvidenceItemEntity;
import com.capstone.fraudcase.persistence.entity.FraudCaseEntity;
import com.capstone.fraudcase.persistence.entity.RecommendationEntity;
import com.capstone.fraudcase.persistence.entity.ToolTraceEntity;
import com.capstone.fraudcase.persistence.entity.TransactionClaimEntity;
import com.capstone.fraudcase.persistence.repository.AuditEventRepository;
import com.capstone.fraudcase.persistence.repository.CaseMessageRepository;
import com.capstone.fraudcase.persistence.repository.EvidenceItemRepository;
import com.capstone.fraudcase.persistence.repository.FraudCaseRepository;
import com.capstone.fraudcase.persistence.repository.PolicyCitationRepository;
import com.capstone.fraudcase.persistence.repository.PolicyRetrievalTraceRepository;
import com.capstone.fraudcase.persistence.repository.RecommendationRepository;
import com.capstone.fraudcase.persistence.repository.ToolTraceRepository;
import com.capstone.fraudcase.persistence.repository.TransactionClaimRepository;
import com.capstone.fraudcase.support.InMemoryVectorStore;
import com.capstone.fraudcase.support.SyntheticCases;
import com.capstone.fraudcase.tools.ToolInvoker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InvestigationOrchestratorTest {

    @Mock
    private FraudCaseRepository cases;
    @Mock
    private TransactionClaimRepository claims;
    @Mock
    private ToolTraceRepository traces;
    @Mock
    private EvidenceItemRepository evidence;
    @Mock
    private RecommendationRepository recommendations;
    @Mock
    private PolicyCitationRepository citations;
    @Mock
    private AuditEventRepository auditRepository;

    private ToolInvoker toolInvoker;
    private FraudCaseEntity fraudCase;

    @BeforeEach
    void setUp() {
        toolInvoker = SyntheticCases.invoker();
        fraudCase = new FraudCaseEntity(SyntheticCases.CUSTOMER, SyntheticCases.CARD, "I do not recognise this.",
                "analyst-1");
        when(cases.findById(fraudCase.getId())).thenReturn(Optional.of(fraudCase));
        when(traces.save(any())).thenAnswer(i -> i.getArgument(0));
        when(evidence.save(any())).thenAnswer(i -> i.getArgument(0));
        when(recommendations.save(any())).thenAnswer(i -> i.getArgument(0));
        when(auditRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        toolInvoker.close();
    }

    private InvestigationOrchestrator orchestrator(FraudCaseProcessor processor) {
        PolicyRetriever retriever = new PolicyRetriever(new InMemoryVectorStore(),
                new LexicalHashEmbeddingModel(), new PolicyProperties(4, 0.72, 800, 64, false));
        return new InvestigationOrchestrator(cases, claims, traces, evidence, recommendations, citations, toolInvoker,
                processor, new RecommendationValidator(), new AuditTrail(auditRepository), SyntheticCases.FIXED_CLOCK,
                new RulesCaseAssistant(), mock(CaseMessageRepository.class),
                new CaseConversationMemory(mock(CaseMessageRepository.class), new MemoryProperties(20)), retriever,
                mock(PolicyRetrievalTraceRepository.class));
    }

    private void withClaim(String reference, ClaimType type) {
        when(claims.findByCaseId(fraudCase.getId())).thenReturn(Optional.of(new TransactionClaimEntity(
                fraudCase.getId(), SyntheticCases.claim(reference, type, new java.math.BigDecimal("249.99")))));
    }

    private void verifyAudited(AuditEventType type, int times) {
        verify(auditRepository, times(times)).save(argThat(e -> e != null && e.getEventType() == type));
    }

    @Test
    void investigate_fullPlan_persistsTracesEvidenceRecommendationAndAudit_slaGapMakesIncomplete() {
        withClaim("txn_fraud_001", ClaimType.UNRECOGNISED_TRANSACTION);

        InvestigationResult result = orchestrator(new RulesFraudCaseAgent())
                .investigate(fraudCase.getId(), "analyst-1", "corr-1");

        assertThat(result.recommendation().classification()).isEqualTo(Classification.UNAUTHORISED_TRANSACTION);
        assertThat(result.fallbackUsed()).isFalse();
        assertThat(result.evidence()).hasSize(InvestigationOrchestrator.EVIDENCE_PLAN.size());
        verify(traces, times(6)).save(any(ToolTraceEntity.class));
        verify(evidence, times(6)).save(any(EvidenceItemEntity.class));
        verify(recommendations).save(any(RecommendationEntity.class));
        verify(cases).save(fraudCase);
        verifyAudited(AuditEventType.TOOL_INVOKED, 6);
        verifyAudited(AuditEventType.EVIDENCE_RECORDED, 6);
        verifyAudited(AuditEventType.RECOMMENDATION_RECORDED, 1);
        verifyAudited(AuditEventType.STATUS_CHANGED, 2);
        assertThat(result.status()).isEqualTo(CaseStatus.INCOMPLETE);
        assertThat(result.evidence()).filteredOn(e -> !e.isAvailable()).singleElement()
                .satisfies(e -> assertThat(e.tool().toolId()).isEqualTo("calculateCaseSla"));
    }

    @Test
    void investigate_evidenceItemsCarryProvenance() {
        withClaim("txn_genuine_001", ClaimType.UNRECOGNISED_TRANSACTION);

        InvestigationResult result = orchestrator(new RulesFraudCaseAgent())
                .investigate(fraudCase.getId(), "analyst-1", "corr-1");

        assertThat(result.evidence()).filteredOn(e -> e.isAvailable()).allSatisfy(e -> {
            assertThat(e.toolTraceId()).isNotBlank();
            assertThat(e.source()).isEqualTo("synthetic-fixture:v1:fraud-training-v1");
            assertThat(e.contentHash()).hasSize(64);
            assertThat(e.payload()).isNotBlank();
            assertThat(e.caseId()).isEqualTo(fraudCase.getId());
        });
    }

    @Test
    void t07_processorThrows_safeFallbackRecordedAndRejectionAudited() {
        withClaim("txn_fraud_001", ClaimType.UNRECOGNISED_TRANSACTION);
        FraudCaseProcessor failing = mock(FraudCaseProcessor.class);
        when(failing.analyze(any(FraudCaseContext.class))).thenThrow(new IllegalStateException("model down"));

        InvestigationResult result = orchestrator(failing).investigate(fraudCase.getId(), "analyst-1", "corr-1");

        assertThat(result.fallbackUsed()).isTrue();
        assertThat(result.recommendation().route()).isEqualTo(Route.MANUAL_REVIEW);
        assertThat(result.recommendation().classification()).isEqualTo(Classification.INSUFFICIENT_EVIDENCE);
        assertThat(result.recommendation().humanReviewRequired()).isTrue();
        assertThat(result.status()).isEqualTo(CaseStatus.INCOMPLETE);
        verifyAudited(AuditEventType.RECOMMENDATION_REJECTED, 1);
        verify(auditRepository, atLeastOnce()).save(argThat(e -> e.getEventType() == AuditEventType.RECOMMENDATION_REJECTED
                && e.getDetails().contains("processor failure") && !e.getDetails().contains("model down")));
    }

    @Test
    void t09_processorReturnsRecommendationForAnotherCase_rejectedAndFallbackUsed() {
        withClaim("txn_fraud_001", ClaimType.UNRECOGNISED_TRANSACTION);
        FraudCaseProcessor rogue = ctx -> TestFixtures.recommendation("some-other-case", List.of("ev-x"));

        InvestigationResult result = orchestrator(rogue).investigate(fraudCase.getId(), "analyst-1", "corr-1");

        assertThat(result.fallbackUsed()).isTrue();
        assertThat(result.recommendation().caseId()).isEqualTo(fraudCase.getId());
        verify(auditRepository, atLeastOnce()).save(argThat(e -> e.getEventType() == AuditEventType.RECOMMENDATION_REJECTED
                && e.getDetails().contains("failed validation")));
    }

    @Test
    void t04_noClaimOnFile_noToolsInvokedAndIncomplete() {
        when(claims.findByCaseId(fraudCase.getId())).thenReturn(Optional.empty());

        InvestigationResult result = orchestrator(new RulesFraudCaseAgent())
                .investigate(fraudCase.getId(), "analyst-1", "corr-1");

        assertThat(result.status()).isEqualTo(CaseStatus.INCOMPLETE);
        assertThat(result.recommendation().classification()).isEqualTo(Classification.INSUFFICIENT_EVIDENCE);
        assertThat(result.evidence()).isEmpty();
        verify(traces, never()).save(any());
        verify(evidence, never()).save(any());
    }

    @Test
    void investigate_unknownCase_throwsWithoutSideEffects() {
        when(cases.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orchestrator(new RulesFraudCaseAgent()).investigate("missing", "analyst-1", null))
                .isInstanceOf(CaseNotFoundException.class);
        verify(auditRepository, never()).save(any());
    }

    @Test
    void investigate_caseAlreadyReadyForReview_rejectedByStateMachine() {
        fraudCase.transitionTo(CaseStatus.ENRICHING);
        fraudCase.transitionTo(CaseStatus.READY_FOR_REVIEW);

        assertThatThrownBy(() -> orchestrator(new RulesFraudCaseAgent())
                .investigate(fraudCase.getId(), "analyst-1", null)).isInstanceOf(InvalidCaseTransitionException.class);
        verify(auditRepository, never()).save(any());
        verify(traces, never()).save(any());
    }

    @Test
    void investigate_statusTransitionsAudited_withActors() {
        withClaim("txn_genuine_001", ClaimType.UNRECOGNISED_TRANSACTION);

        orchestrator(new RulesFraudCaseAgent()).investigate(fraudCase.getId(), "analyst-7", "corr-1");

        verify(auditRepository).save(argThat(e -> e.getEventType() == AuditEventType.STATUS_CHANGED
                && e.getFromStatus() == CaseStatus.NEW && e.getToStatus() == CaseStatus.ENRICHING
                && e.getActorId().equals("analyst-7")));
        verify(auditRepository).save(argThat(e -> e.getEventType() == AuditEventType.STATUS_CHANGED
                && e.getFromStatus() == CaseStatus.ENRICHING && e.getToStatus() == CaseStatus.INCOMPLETE
                && e.getActorId().equals(AuditTrail.SYSTEM_ACTOR)));
    }
}
