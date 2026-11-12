package com.capstone.fraudcase.knowledge;

import com.capstone.fraudcase.agent.common.RecommendationValidator;
import com.capstone.fraudcase.agent.rules.RulesFraudCaseAgent;
import com.capstone.fraudcase.application.InvestigationOrchestrator;
import com.capstone.fraudcase.memory.CaseConversationMemory;
import com.capstone.fraudcase.application.InvestigationResult;
import com.capstone.fraudcase.domain.ClaimType;
import com.capstone.fraudcase.domain.Classification;
import com.capstone.fraudcase.domain.Route;
import com.capstone.fraudcase.persistence.MySqlRepositoryTest;
import com.capstone.fraudcase.persistence.audit.AuditTrail;
import com.capstone.fraudcase.persistence.entity.FraudCaseEntity;
import com.capstone.fraudcase.persistence.entity.TransactionClaimEntity;
import com.capstone.fraudcase.persistence.repository.FraudCaseRepository;
import com.capstone.fraudcase.persistence.repository.PolicyCitationRepository;
import com.capstone.fraudcase.persistence.repository.TransactionClaimRepository;
import com.capstone.fraudcase.support.SyntheticCases;
import com.capstone.fraudcase.tools.ToolInvoker;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end RAG on real MySQL: ingestion stores documents/chunks/embeddings; an investigation runs the
 * retrieval, persists the trace and citations, and abstains safely to MANUAL_REVIEW when nothing qualifies.
 */
@MySqlRepositoryTest
@Import(PolicyRetrievalMySqlTest.RagComponents.class)
class PolicyRetrievalMySqlTest {

    private static final String FRAUD_PASSAGE =
            "An unauthorized transaction report from the cardholder starts a fraud investigation "
                    + "and may lead to a chargeback.";

    @TestConfiguration(proxyBeanMethods = false)
    @ComponentScan(basePackageClasses = {InvestigationOrchestrator.class, ToolInvoker.class,
            RulesFraudCaseAgent.class, RecommendationValidator.class, AuditTrail.class, PolicyRetriever.class,
            CaseConversationMemory.class})
    static class RagComponents {
    }

    @Autowired
    private InvestigationOrchestrator orchestrator;
    @Autowired
    private PolicyIngestionService ingestion;
    @Autowired
    private FraudCaseRepository cases;
    @Autowired
    private TransactionClaimRepository claims;
    @Autowired
    private PolicyCitationRepository citations;
    @Autowired
    private EntityManager em;
    @Autowired
    private JdbcTemplate jdbc;

    /** Each test owns the corpus: the startup seeder may have committed sample documents already. */
    @BeforeEach
    void clearCorpus() {
        jdbc.update("DELETE FROM policy_chunk");
        jdbc.update("DELETE FROM policy_document");
    }

    private String newCase(String reference, ClaimType claimType) {
        FraudCaseEntity fraudCase = cases.saveAndFlush(new FraudCaseEntity(SyntheticCases.CUSTOMER,
                SyntheticCases.CARD, "I do not recognise this purchase.", "analyst-it"));
        claims.saveAndFlush(new TransactionClaimEntity(fraudCase.getId(),
                SyntheticCases.claim(reference, claimType, new BigDecimal("249.99"))));
        em.clear();
        return fraudCase.getId();
    }

    private InvestigationResult investigate(String caseId) {
        InvestigationResult result = orchestrator.investigate(caseId, "analyst-it", "corr-rag");
        em.flush();
        em.clear();
        return result;
    }

    private void ingestFraudPolicy() {
        ingestion.ingest(new PolicyDocument("pol-fraud", "Unauthorized Transaction Handling", "1.0",
                LocalDate.of(2026, 1, 1), "Fraud Operations", PolicyClassification.INTERNAL,
                PolicyStatus.APPROVED, List.of(
                new PolicyDocument.Section("Procedure", FRAUD_PASSAGE))));
        em.flush();
        em.clear();
    }

    @Test
    void ingest_persistsDocumentChunksAndEmbeddings() {
        ingestFraudPolicy();

        Map<String, Object> doc = jdbc.queryForMap(
                "SELECT document_id, title, version, owner, classification, status, content_hash "
                        + "FROM policy_document WHERE document_id = 'pol-fraud'");
        assertThat(doc.get("title")).isEqualTo("Unauthorized Transaction Handling");
        assertThat(doc.get("owner")).isEqualTo("Fraud Operations");
        assertThat(doc.get("status")).isEqualTo("APPROVED");
        assertThat((String) doc.get("content_hash")).hasSize(64);

        List<Map<String, Object>> chunks = jdbc.queryForList(
                "SELECT c.chunk_id, c.section, c.passage, c.content_hash, c.embedding_model, c.embedding "
                        + "FROM policy_chunk c JOIN policy_document d ON d.id = c.document_row_id "
                        + "WHERE d.document_id = 'pol-fraud' ORDER BY c.chunk_index");
        assertThat(chunks).isNotEmpty().allSatisfy(c -> {
            assertThat((String) c.get("chunk_id")).startsWith("pol-fraud:1.0:");
            assertThat((String) c.get("content_hash")).hasSize(64);
            assertThat(c.get("embedding_model")).isEqualTo(LexicalHashEmbeddingModel.MODEL_ID);
            assertThat((String) c.get("embedding")).contains(",");
        });
        assertThat(chunks.stream().map(c -> c.get("passage")).toList()).contains(FRAUD_PASSAGE);
    }

    @Test
    void investigate_groundedRetrieval_persistsCitationsTraceAndHits() {
        ingestFraudPolicy();
        String caseId = newCase("txn_fraud_rag01", ClaimType.UNRECOGNISED_TRANSACTION);

        InvestigationResult result = investigate(caseId);

        assertThat(result.policyGrounded()).isTrue();
        assertThat(result.recommendation().policyCitations()).isNotEmpty();

        List<Map<String, Object>> citationRows = jdbc.queryForList(
                "SELECT document_id, document_version, owner, section, chunk_id, relevance_score, excerpt "
                        + "FROM policy_citation WHERE case_id = ?", caseId);
        assertThat(citationRows).isNotEmpty().allSatisfy(c -> {
            assertThat(c.get("document_id")).isEqualTo("pol-fraud");
            assertThat(c.get("owner")).isEqualTo("Fraud Operations");
            assertThat(((BigDecimal) c.get("relevance_score")).doubleValue()).isGreaterThanOrEqualTo(0.72);
            assertThat((String) c.get("excerpt")).isNotBlank();
        });

        Map<String, Object> trace = jdbc.queryForMap(
                "SELECT grounded, abstain_code, matched, candidates, top_k, similarity_threshold, "
                        + "embedding_model, correlation_id, query_hash FROM policy_retrieval_trace "
                        + "WHERE case_id = ?", caseId);
        assertThat(trace.get("grounded")).isEqualTo(Boolean.TRUE);
        assertThat(trace.get("abstain_code")).isNull();
        assertThat(((Number) trace.get("matched")).intValue()).isGreaterThanOrEqualTo(1);
        assertThat(((Number) trace.get("top_k")).intValue()).isEqualTo(4);
        assertThat(((BigDecimal) trace.get("similarity_threshold")).doubleValue()).isEqualTo(0.72);
        assertThat(trace.get("embedding_model")).isEqualTo(LexicalHashEmbeddingModel.MODEL_ID);
        assertThat(trace.get("correlation_id")).isEqualTo("corr-rag");
        assertThat((String) trace.get("query_hash")).hasSize(64);

        List<Map<String, Object>> hits = jdbc.queryForList(
                "SELECT h.chunk_id, h.included, h.drop_reason, h.similarity FROM policy_retrieval_hit h "
                        + "JOIN policy_retrieval_trace t ON t.id = h.trace_id WHERE t.case_id = ?", caseId);
        assertThat(hits).isNotEmpty().anySatisfy(h ->
                assertThat(h.get("included")).isEqualTo(Boolean.TRUE));
        assertThat(hits).allSatisfy(h ->
                assertThat(((BigDecimal) h.get("similarity")).doubleValue()).isBetween(0.0, 1.0));
    }

    @Test
    void investigate_emptyCorpus_abstainsWithManualReviewRouteAndTrace() {
        String caseId = newCase("txn_fraud_rag02", ClaimType.UNRECOGNISED_TRANSACTION);

        InvestigationResult result = investigate(caseId);

        assertThat(result.policyGrounded()).isFalse();
        assertThat(result.recommendation().route()).isEqualTo(Route.MANUAL_REVIEW);
        assertThat(result.recommendation().classification()).isEqualTo(Classification.UNAUTHORISED_TRANSACTION);
        assertThat(result.recommendation().policyCitations()).isEmpty();
        assertThat(result.recommendation().missingInformation())
                .anySatisfy(m -> assertThat(m).contains("retrieval abstained: NO_MATCH"));

        Map<String, Object> trace = jdbc.queryForMap(
                "SELECT grounded, abstain_code, matched FROM policy_retrieval_trace WHERE case_id = ?",
                caseId);
        assertThat(trace.get("grounded")).isEqualTo(Boolean.FALSE);
        assertThat(trace.get("abstain_code")).isEqualTo("NO_MATCH");
        assertThat(((Number) trace.get("matched")).intValue()).isZero();
    }

    @Test
    void investigate_belowThresholdCorpus_abstainsAndDrivesManualReview() {
        ingestion.ingest(new PolicyDocument("pol-unrelated", "Facilities Handbook", "1.0",
                LocalDate.of(2026, 1, 1), "Facilities", PolicyClassification.INTERNAL,
                PolicyStatus.APPROVED, List.of(
                new PolicyDocument.Section("Scope",
                        "Office kitchen cleaning schedule and weekly supply reorder rules."))));
        em.flush();
        em.clear();
        String caseId = newCase("txn_fraud_rag03", ClaimType.UNRECOGNISED_TRANSACTION);

        InvestigationResult result = investigate(caseId);

        assertThat(result.policyGrounded()).isFalse();
        assertThat(result.recommendation().route()).isEqualTo(Route.MANUAL_REVIEW);
        assertThat(result.recommendation().missingInformation())
                .anySatisfy(m -> assertThat(m).contains("retrieval abstained: BELOW_THRESHOLD"));

        Map<String, Object> trace = jdbc.queryForMap(
                "SELECT abstain_code FROM policy_retrieval_trace WHERE case_id = ?", caseId);
        assertThat(trace.get("abstain_code")).isEqualTo("BELOW_THRESHOLD");
    }
}
