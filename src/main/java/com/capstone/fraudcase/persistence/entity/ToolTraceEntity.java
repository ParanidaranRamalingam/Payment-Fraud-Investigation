package com.capstone.fraudcase.persistence.entity;

import com.capstone.fraudcase.domain.EvidenceTool;
import com.capstone.fraudcase.domain.ToolTrace;
import com.capstone.fraudcase.domain.ToolTraceStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "tool_trace")
public class ToolTraceEntity extends AssignedIdEntity {

    @Column(name = "case_id", length = 36, nullable = false, updatable = false)
    private String caseId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "tool", length = 40, nullable = false, updatable = false)
    private EvidenceTool tool;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", length = 16, nullable = false)
    private ToolTraceStatus status;

    @Column(name = "request_summary", length = 1000)
    private String requestSummary;

    @Column(name = "result_summary", columnDefinition = "TEXT")
    private String resultSummary;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "correlation_id", length = 64, updatable = false)
    private String correlationId;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "duration_ms")
    private Long durationMs;

    protected ToolTraceEntity() {
    }

    public ToolTraceEntity(String caseId, EvidenceTool tool, String requestSummary, String correlationId,
                           Instant startedAt) {
        super(true);
        this.caseId = Objects.requireNonNull(caseId, "caseId");
        this.tool = Objects.requireNonNull(tool, "tool");
        this.requestSummary = requestSummary;
        this.correlationId = correlationId;
        this.startedAt = toDbPrecision(Objects.requireNonNull(startedAt, "startedAt"));
        this.status = ToolTraceStatus.ERROR;
    }

    public void complete(ToolTraceStatus status, String resultSummary, String errorCode, Instant completedAt) {
        this.status = Objects.requireNonNull(status, "status");
        this.resultSummary = resultSummary;
        this.errorCode = errorCode;
        this.completedAt = toDbPrecision(Objects.requireNonNull(completedAt, "completedAt"));
        this.durationMs = Math.max(0, Duration.between(startedAt, this.completedAt).toMillis());
    }

    public ToolTrace toDomain() {
        return new ToolTrace(getId(), caseId, tool, status, requestSummary, resultSummary, errorCode, correlationId,
                startedAt, completedAt, durationMs);
    }

    public String getCaseId() {
        return caseId;
    }

    public EvidenceTool getTool() {
        return tool;
    }

    public ToolTraceStatus getStatus() {
        return status;
    }

    public String getRequestSummary() {
        return requestSummary;
    }

    public String getResultSummary() {
        return resultSummary;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Long getDurationMs() {
        return durationMs;
    }
}
