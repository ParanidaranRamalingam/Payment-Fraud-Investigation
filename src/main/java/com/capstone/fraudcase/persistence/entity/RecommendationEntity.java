package com.capstone.fraudcase.persistence.entity;

import com.capstone.fraudcase.domain.Classification;
import com.capstone.fraudcase.domain.FraudCaseRecommendation;
import com.capstone.fraudcase.domain.PolicyCitation;
import com.capstone.fraudcase.domain.ProcessorMode;
import com.capstone.fraudcase.domain.RiskLevel;
import com.capstone.fraudcase.domain.Route;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Persisted form of a validated {@link FraudCaseRecommendation}. Only constructible from the domain record,
 * so a recommendation that violates the contract cannot reach the database. Citations are stored separately
 * in {@link PolicyCitationEntity}.
 */
@Entity
@Immutable
@Table(name = "recommendation")
public class RecommendationEntity extends AssignedIdEntity {

    @Column(name = "case_id", length = 36, nullable = false)
    private String caseId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "classification", length = 40, nullable = false)
    private Classification classification;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "risk_level", length = 16, nullable = false)
    private RiskLevel riskLevel;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "route", length = 32, nullable = false)
    private Route route;

    @Column(name = "confidence", precision = 4, scale = 3, nullable = false)
    private BigDecimal confidence;

    @Column(name = "human_review_required", nullable = false)
    private boolean humanReviewRequired;

    @Column(name = "rationale", columnDefinition = "TEXT", nullable = false)
    private String rationale;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "processor_mode", length = 16, nullable = false)
    private ProcessorMode processorMode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @ElementCollection
    @CollectionTable(name = "recommendation_evidence", joinColumns = @JoinColumn(name = "recommendation_id"))
    @OrderColumn(name = "item_order")
    @Column(name = "evidence_item_id", length = 36, nullable = false)
    private List<String> evidenceIds = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "recommendation_missing_info", joinColumns = @JoinColumn(name = "recommendation_id"))
    @OrderColumn(name = "item_order")
    @Column(name = "description", length = 500, nullable = false)
    private List<String> missingInformation = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "recommendation_trace_ref", joinColumns = @JoinColumn(name = "recommendation_id"))
    @OrderColumn(name = "item_order")
    @Column(name = "trace_ref", length = 64, nullable = false)
    private List<String> traceIds = new ArrayList<>();

    protected RecommendationEntity() {
    }

    public RecommendationEntity(FraudCaseRecommendation recommendation) {
        super(true);
        this.caseId = recommendation.caseId();
        this.classification = recommendation.classification();
        this.riskLevel = recommendation.riskLevel();
        this.route = recommendation.route();
        this.confidence = recommendation.confidence();
        this.humanReviewRequired = recommendation.humanReviewRequired();
        this.rationale = recommendation.rationale();
        this.processorMode = recommendation.processorMode();
        this.evidenceIds = new ArrayList<>(recommendation.evidenceIds());
        this.missingInformation = new ArrayList<>(recommendation.missingInformation());
        this.traceIds = new ArrayList<>(recommendation.traceIds());
    }

    @PrePersist
    void onCreate() {
        this.createdAt = now();
    }

    public FraudCaseRecommendation toDomain(List<PolicyCitation> citations) {
        return new FraudCaseRecommendation(caseId, classification, riskLevel, route, confidence,
                humanReviewRequired, evidenceIds, citations, missingInformation, traceIds, rationale, processorMode);
    }

    public String getCaseId() {
        return caseId;
    }

    public Classification getClassification() {
        return classification;
    }

    public Route getRoute() {
        return route;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public boolean isHumanReviewRequired() {
        return humanReviewRequired;
    }

    public List<String> getEvidenceIds() {
        return List.copyOf(evidenceIds);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
