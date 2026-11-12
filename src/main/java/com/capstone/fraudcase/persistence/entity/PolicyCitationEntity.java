package com.capstone.fraudcase.persistence.entity;

import com.capstone.fraudcase.domain.PolicyCitation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

@Entity
@Immutable
@Table(name = "policy_citation")
public class PolicyCitationEntity extends AssignedIdEntity {

    @Column(name = "recommendation_id", length = 36, nullable = false)
    private String recommendationId;

    @Column(name = "case_id", length = 36, nullable = false)
    private String caseId;

    @Column(name = "document_id", length = 64, nullable = false)
    private String documentId;

    @Column(name = "document_title", length = 200, nullable = false)
    private String documentTitle;

    @Column(name = "document_version", length = 32, nullable = false)
    private String documentVersion;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(name = "section", length = 120, nullable = false)
    private String section;

    @Column(name = "owner", length = 120, nullable = false)
    private String owner;

    @Column(name = "chunk_id", length = 64, nullable = false)
    private String chunkId;

    @Column(name = "relevance_score", precision = 5, scale = 4, nullable = false)
    private BigDecimal relevanceScore;

    @Column(name = "excerpt", columnDefinition = "TEXT", nullable = false)
    private String excerpt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PolicyCitationEntity() {
    }

    public PolicyCitationEntity(String recommendationId, String caseId, PolicyCitation citation) {
        super(true);
        this.recommendationId = Objects.requireNonNull(recommendationId, "recommendationId");
        this.caseId = Objects.requireNonNull(caseId, "caseId");
        this.documentId = citation.documentId();
        this.documentTitle = citation.documentTitle();
        this.documentVersion = citation.documentVersion();
        this.effectiveDate = citation.effectiveDate();
        this.section = citation.section();
        this.owner = citation.owner();
        this.chunkId = citation.chunkId();
        this.relevanceScore = BigDecimal.valueOf(citation.relevanceScore()).setScale(4, RoundingMode.HALF_UP);
        this.excerpt = citation.excerpt();
    }

    @PrePersist
    void onCreate() {
        this.createdAt = now();
    }

    public PolicyCitation toDomain() {
        return new PolicyCitation(documentId, documentTitle, documentVersion, effectiveDate, section, owner,
                chunkId, relevanceScore.doubleValue(), excerpt);
    }

    public String getRecommendationId() {
        return recommendationId;
    }

    public String getCaseId() {
        return caseId;
    }
}
