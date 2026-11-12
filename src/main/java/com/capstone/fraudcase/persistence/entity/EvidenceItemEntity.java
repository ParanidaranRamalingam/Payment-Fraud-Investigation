package com.capstone.fraudcase.persistence.entity;

import com.capstone.fraudcase.domain.EvidenceItem;
import com.capstone.fraudcase.domain.EvidenceStatus;
import com.capstone.fraudcase.domain.EvidenceTool;
import com.capstone.fraudcase.domain.ProvenanceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Objects;

@Entity
@Immutable
@Table(name = "evidence_item")
public class EvidenceItemEntity extends AssignedIdEntity {

    @Column(name = "case_id", length = 36, nullable = false)
    private String caseId;

    @Column(name = "tool_trace_id", length = 36)
    private String toolTraceId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "tool", length = 40)
    private EvidenceTool tool;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "provenance", length = 32, nullable = false)
    private ProvenanceType provenance;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", length = 16, nullable = false)
    private EvidenceStatus status;

    @Column(name = "summary", columnDefinition = "TEXT", nullable = false)
    private String summary;

    @Column(name = "collected_at", nullable = false)
    private Instant collectedAt;

    protected EvidenceItemEntity() {
    }

    public EvidenceItemEntity(String caseId, EvidenceTool tool, ProvenanceType provenance, EvidenceStatus status,
                              String summary, String toolTraceId, Instant collectedAt) {
        super(true);
        this.caseId = Objects.requireNonNull(caseId, "caseId");
        this.tool = tool;
        this.provenance = provenance;
        this.status = status;
        this.summary = summary;
        this.toolTraceId = toolTraceId;
        this.collectedAt = toDbPrecision(collectedAt);
        toDomain();
    }

    /** Domain view; also re-validates invariants (e.g. tool evidence must reference a trace). */
    public EvidenceItem toDomain() {
        return new EvidenceItem(getId(), caseId, tool, provenance, status, summary, toolTraceId, collectedAt);
    }

    public String getCaseId() {
        return caseId;
    }

    public String getToolTraceId() {
        return toolTraceId;
    }

    public EvidenceStatus getStatus() {
        return status;
    }
}
