package com.capstone.fraudcase.persistence.entity;

import com.capstone.fraudcase.domain.CaseStateMachine;
import com.capstone.fraudcase.domain.CaseStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "fraud_case")
public class FraudCaseEntity extends AssignedIdEntity {

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", length = 32, nullable = false)
    private CaseStatus status;

    @Column(name = "customer_reference", length = 64, nullable = false, updatable = false)
    private String customerReference;

    @Column(name = "card_reference", length = 64, updatable = false)
    private String cardReference;

    @Column(name = "customer_statement", columnDefinition = "TEXT", nullable = false, updatable = false)
    private String customerStatement;

    @Column(name = "created_by", length = 64, nullable = false, updatable = false)
    private String createdBy;

    @Column(name = "synthetic_data_confirmed", nullable = false, updatable = false)
    private boolean syntheticDataConfirmed;

    @Column(name = "human_review_required", nullable = false)
    private boolean humanReviewRequired;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected FraudCaseEntity() {
    }

    public FraudCaseEntity(String customerReference, String cardReference, String customerStatement, String createdBy) {
        super(true);
        this.status = CaseStatus.NEW;
        this.customerReference = Objects.requireNonNull(customerReference, "customerReference");
        this.cardReference = cardReference;
        this.customerStatement = Objects.requireNonNull(customerStatement, "customerStatement");
        this.createdBy = Objects.requireNonNull(createdBy, "createdBy");
        this.syntheticDataConfirmed = true;
        this.humanReviewRequired = true;
    }

    /** Applies a deterministic lifecycle transition; throws if the state machine forbids it. */
    public CaseStatus transitionTo(CaseStatus target) {
        CaseStatus previous = status;
        this.status = CaseStateMachine.requireTransition(status, target);
        return previous;
    }

    @PrePersist
    void onCreate() {
        Instant now = now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = now();
    }

    public CaseStatus getStatus() {
        return status;
    }

    public String getCustomerReference() {
        return customerReference;
    }

    public String getCardReference() {
        return cardReference;
    }

    public String getCustomerStatement() {
        return customerStatement;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public boolean isSyntheticDataConfirmed() {
        return syntheticDataConfirmed;
    }

    public boolean isHumanReviewRequired() {
        return humanReviewRequired;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}
