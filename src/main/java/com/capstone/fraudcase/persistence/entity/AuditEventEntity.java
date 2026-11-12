package com.capstone.fraudcase.persistence.entity;

import com.capstone.fraudcase.domain.ActorType;
import com.capstone.fraudcase.domain.AuditEventType;
import com.capstone.fraudcase.domain.CaseStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Objects;

/**
 * Append-only audit record. {@link Immutable} stops Hibernate from issuing UPDATEs, the lifecycle callbacks
 * reject removal, and {@code AuditEventRepository} exposes no update/delete methods. Details must already be
 * redacted by the caller.
 */
@Entity
@Immutable
@Table(name = "audit_event")
public class AuditEventEntity extends AssignedIdEntity {

    public static final int MAX_DETAILS_LENGTH = 2000;

    @Column(name = "case_id", length = 36, updatable = false)
    private String caseId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "event_type", length = 40, nullable = false, updatable = false)
    private AuditEventType eventType;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "actor_type", length = 16, nullable = false, updatable = false)
    private ActorType actorType;

    @Column(name = "actor_id", length = 64, nullable = false, updatable = false)
    private String actorId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "from_status", length = 32, updatable = false)
    private CaseStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "to_status", length = 32, updatable = false)
    private CaseStatus toStatus;

    @Column(name = "details", length = MAX_DETAILS_LENGTH, updatable = false)
    private String details;

    @Column(name = "correlation_id", length = 64, updatable = false)
    private String correlationId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected AuditEventEntity() {
    }

    public AuditEventEntity(String caseId, AuditEventType eventType, ActorType actorType, String actorId,
                            CaseStatus fromStatus, CaseStatus toStatus, String details, String correlationId) {
        super(true);
        this.caseId = caseId;
        this.eventType = Objects.requireNonNull(eventType, "eventType");
        this.actorType = Objects.requireNonNull(actorType, "actorType");
        this.actorId = Objects.requireNonNull(actorId, "actorId");
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        if (details != null && details.length() > MAX_DETAILS_LENGTH) {
            throw new IllegalArgumentException("audit details must be at most " + MAX_DETAILS_LENGTH + " characters");
        }
        this.details = details;
        this.correlationId = correlationId;
        this.occurredAt = now();
    }

    @PreUpdate
    @PreRemove
    void rejectMutation() {
        throw new UnsupportedOperationException("audit events are append-only");
    }

    public String getCaseId() {
        return caseId;
    }

    public AuditEventType getEventType() {
        return eventType;
    }

    public ActorType getActorType() {
        return actorType;
    }

    public String getActorId() {
        return actorId;
    }

    public CaseStatus getFromStatus() {
        return fromStatus;
    }

    public CaseStatus getToStatus() {
        return toStatus;
    }

    public String getDetails() {
        return details;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
