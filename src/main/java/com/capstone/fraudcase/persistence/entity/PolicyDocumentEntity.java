package com.capstone.fraudcase.persistence.entity;

import com.capstone.fraudcase.knowledge.PolicyClassification;
import com.capstone.fraudcase.knowledge.PolicyDocument;
import com.capstone.fraudcase.knowledge.PolicyStatus;
import com.capstone.fraudcase.knowledge.PolicyVersionInfo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;

/**
 * One stored policy document version. Immutable: re-ingesting a version deletes and re-inserts the row,
 * so stored metadata always matches what the ingestion service validated.
 */
@Entity
@Immutable
@Table(name = "policy_document")
public class PolicyDocumentEntity extends AssignedIdEntity {

    @Column(name = "document_id", length = 64, nullable = false)
    private String documentId;

    @Column(name = "title", length = 200, nullable = false)
    private String title;

    @Column(name = "version", length = 32, nullable = false)
    private String version;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(name = "owner", length = 120, nullable = false)
    private String owner;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "classification", length = 16, nullable = false)
    private PolicyClassification classification;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", length = 16, nullable = false)
    private PolicyStatus status;

    @Column(name = "content_hash", length = 64, nullable = false)
    private String contentHash;

    @Column(name = "ingested_at", nullable = false)
    private Instant ingestedAt;

    protected PolicyDocumentEntity() {
    }

    public PolicyDocumentEntity(PolicyDocument document) {
        super(true);
        this.documentId = document.documentId();
        this.title = document.title();
        this.version = document.version();
        this.effectiveDate = document.effectiveDate();
        this.owner = document.owner();
        this.classification = document.classification();
        this.status = document.status();
        this.contentHash = document.contentHash();
    }

    @PrePersist
    void onCreate() {
        this.ingestedAt = now();
    }

    public PolicyVersionInfo toVersionInfo() {
        return new PolicyVersionInfo(documentId, version, effectiveDate, status);
    }

    public String getDocumentId() {
        return documentId;
    }

    public String getTitle() {
        return title;
    }

    public String getVersion() {
        return version;
    }

    public LocalDate getEffectiveDate() {
        return effectiveDate;
    }

    public String getOwner() {
        return owner;
    }

    public PolicyClassification getClassification() {
        return classification;
    }

    public PolicyStatus getStatus() {
        return status;
    }

    public String getContentHash() {
        return contentHash;
    }

    public Instant getIngestedAt() {
        return ingestedAt;
    }
}
