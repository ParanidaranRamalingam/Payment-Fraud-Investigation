package com.capstone.fraudcase.persistence.entity;

import com.capstone.fraudcase.knowledge.AbstainCode;
import com.capstone.fraudcase.knowledge.RetrievalOutcome;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Auditable record of one policy retrieval run: what was searched (hash only, never the raw case-derived
 * query text), the configured top-k/threshold, how many candidates were seen and kept, whether grounding
 * succeeded, and — when it did not — the explicit abstention code. Hits preserve every evaluated candidate
 * with its verdict, so an abstention is explainable after the fact.
 */
@Entity
@Immutable
@Table(name = "policy_retrieval_trace")
public class PolicyRetrievalTraceEntity extends AssignedIdEntity {

    @Column(name = "case_id", length = 36, nullable = false)
    private String caseId;

    @Column(name = "query_hash", length = 64, nullable = false)
    private String queryHash;

    @Column(name = "query_term_count", nullable = false)
    private int queryTermCount;

    @Column(name = "top_k", nullable = false)
    private int topK;

    @Column(name = "similarity_threshold", precision = 5, scale = 4, nullable = false)
    private BigDecimal similarityThreshold;

    @Column(name = "candidates", nullable = false)
    private int candidates;

    @Column(name = "matched", nullable = false)
    private int matched;

    @Column(name = "grounded", nullable = false)
    private boolean grounded;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "abstain_code", length = 32)
    private AbstainCode abstainCode;

    @Column(name = "embedding_model", length = 64, nullable = false)
    private String embeddingModel;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @ElementCollection
    @CollectionTable(name = "policy_retrieval_hit", joinColumns = @JoinColumn(name = "trace_id"))
    @OrderColumn(name = "item_order")
    private List<PolicyRetrievalHit> hits = new ArrayList<>();

    protected PolicyRetrievalTraceEntity() {
    }

    public PolicyRetrievalTraceEntity(String caseId, RetrievalOutcome outcome, String correlationId) {
        super(true);
        this.caseId = caseId;
        this.queryHash = outcome.queryHash();
        this.queryTermCount = outcome.queryTermCount();
        this.topK = outcome.topK();
        this.similarityThreshold = BigDecimal.valueOf(outcome.similarityThreshold())
                .setScale(4, RoundingMode.HALF_UP);
        this.candidates = outcome.candidates().size();
        this.matched = outcome.citations().size();
        this.grounded = outcome.grounded();
        this.abstainCode = outcome.abstainCode();
        this.embeddingModel = outcome.embeddingModel();
        this.correlationId = correlationId;
        this.hits = outcome.candidates().stream().map(PolicyRetrievalHit::new).toList();
    }

    @PrePersist
    void onCreate() {
        this.createdAt = now();
    }

    public String getCaseId() {
        return caseId;
    }

    public List<PolicyRetrievalHit> getHits() {
        return List.copyOf(hits);
    }
}
