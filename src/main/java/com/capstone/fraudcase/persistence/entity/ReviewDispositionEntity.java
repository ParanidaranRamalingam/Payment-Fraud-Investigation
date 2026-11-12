package com.capstone.fraudcase.persistence.entity;

import com.capstone.fraudcase.domain.Classification;
import com.capstone.fraudcase.domain.ReviewDecision;
import com.capstone.fraudcase.domain.ReviewDisposition;
import com.capstone.fraudcase.domain.Route;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Immutable
@Table(name = "review_disposition")
public class ReviewDispositionEntity extends AssignedIdEntity {

    @Column(name = "case_id", length = 36, nullable = false)
    private String caseId;

    @Column(name = "recommendation_id", length = 36)
    private String recommendationId;

    @Column(name = "reviewer_id", length = 64, nullable = false)
    private String reviewerId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "decision", length = 16, nullable = false)
    private ReviewDecision decision;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "final_classification", length = 40)
    private Classification finalClassification;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "final_route", length = 32)
    private Route finalRoute;

    @Column(name = "rationale", columnDefinition = "TEXT", nullable = false)
    private String rationale;

    @Column(name = "reviewed_at", nullable = false)
    private Instant reviewedAt;

    protected ReviewDispositionEntity() {
    }

    public ReviewDispositionEntity(ReviewDisposition disposition) {
        super(true);
        this.caseId = disposition.caseId();
        this.recommendationId = disposition.recommendationId();
        this.reviewerId = disposition.reviewerId();
        this.decision = disposition.decision();
        this.finalClassification = disposition.finalClassification();
        this.finalRoute = disposition.finalRoute();
        this.rationale = disposition.rationale();
        this.reviewedAt = toDbPrecision(disposition.reviewedAt());
    }

    public ReviewDisposition toDomain() {
        return new ReviewDisposition(caseId, recommendationId, reviewerId, decision, finalClassification, finalRoute,
                rationale, reviewedAt);
    }

    public String getCaseId() {
        return caseId;
    }

    public ReviewDecision getDecision() {
        return decision;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }
}
