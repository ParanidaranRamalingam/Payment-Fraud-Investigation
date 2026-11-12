package com.capstone.fraudcase.persistence.entity;

import com.capstone.fraudcase.domain.CaseMessage;
import com.capstone.fraudcase.domain.MessageRole;
import com.capstone.fraudcase.domain.ProcessorMode;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Immutable, case-scoped conversation entry. Constructed only through the validating domain record. */
@Entity
@Immutable
@Table(name = "case_message")
public class CaseMessageEntity extends AssignedIdEntity {

    @Column(name = "case_id", length = 36, nullable = false, updatable = false)
    private String caseId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "role", length = 16, nullable = false, updatable = false)
    private MessageRole role;

    @Column(name = "author_id", length = 64, nullable = false, updatable = false)
    private String authorId;

    @Column(name = "content", columnDefinition = "TEXT", nullable = false, updatable = false)
    private String content;

    @Column(name = "reply_to_id", length = 36, updatable = false)
    private String replyToId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "processor_mode", length = 16, updatable = false)
    private ProcessorMode processorMode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @ElementCollection
    @CollectionTable(name = "case_message_evidence", joinColumns = @JoinColumn(name = "message_id"))
    @OrderColumn(name = "item_order")
    @Column(name = "evidence_item_id", length = 36, nullable = false)
    private List<String> evidenceIds = new ArrayList<>();

    protected CaseMessageEntity() {
    }

    private CaseMessageEntity(String caseId, MessageRole role, String authorId, String content, String replyToId,
                              List<String> evidenceIds, ProcessorMode processorMode, Instant createdAt) {
        super(true);
        this.caseId = Objects.requireNonNull(caseId, "caseId");
        this.role = role;
        this.authorId = authorId;
        this.content = content;
        this.replyToId = replyToId;
        this.evidenceIds = new ArrayList<>(evidenceIds);
        this.processorMode = processorMode;
        this.createdAt = toDbPrecision(createdAt);
        toDomain();
    }

    public static CaseMessageEntity analystMessage(String caseId, String analystId, String content, Instant at) {
        return new CaseMessageEntity(caseId, MessageRole.ANALYST, analystId, content, null, List.of(), null, at);
    }

    public static CaseMessageEntity assistantReply(CaseMessageEntity question, String content, List<String> evidenceIds,
                                                   ProcessorMode mode, String authorId, Instant at) {
        return new CaseMessageEntity(question.caseId, MessageRole.ASSISTANT, authorId, content, question.getId(),
                evidenceIds, mode, at);
    }

    /** Domain view; also re-validates invariants. */
    public CaseMessage toDomain() {
        return new CaseMessage(getId(), caseId, role, authorId, content, replyToId, evidenceIds, processorMode,
                createdAt);
    }

    public String getCaseId() {
        return caseId;
    }

    public MessageRole getRole() {
        return role;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
