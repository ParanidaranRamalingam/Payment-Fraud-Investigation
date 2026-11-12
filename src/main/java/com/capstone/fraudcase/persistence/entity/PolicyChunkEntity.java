package com.capstone.fraudcase.persistence.entity;

import com.capstone.fraudcase.knowledge.EmbeddedChunk;
import com.capstone.fraudcase.knowledge.EmbeddingVectors;
import com.capstone.fraudcase.knowledge.PolicyChunk;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.Objects;

/**
 * One stored policy chunk with its embedding. Document-level citation fields (title, version, effective
 * date, owner, classification) live on {@link PolicyDocumentEntity}; {@code document_id}/
 * {@code document_version} are denormalised here so chunk lookups never depend on row joins.
 */
@Entity
@Immutable
@Table(name = "policy_chunk")
public class PolicyChunkEntity extends AssignedIdEntity {

    @Column(name = "document_row_id", length = 36, nullable = false)
    private String documentRowId;

    @Column(name = "document_id", length = 64, nullable = false)
    private String documentId;

    @Column(name = "document_version", length = 32, nullable = false)
    private String documentVersion;

    @Column(name = "chunk_id", length = 64, nullable = false)
    private String chunkId;

    @Column(name = "section", length = 120, nullable = false)
    private String section;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(name = "passage", columnDefinition = "TEXT", nullable = false)
    private String passage;

    @Column(name = "content_hash", length = 64, nullable = false)
    private String contentHash;

    @Column(name = "embedding_model", length = 64, nullable = false)
    private String embeddingModel;

    @Column(name = "embedding", columnDefinition = "TEXT", nullable = false)
    private String embedding;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PolicyChunkEntity() {
    }

    public PolicyChunkEntity(String documentRowId, EmbeddedChunk embedded) {
        super(true);
        this.documentRowId = Objects.requireNonNull(documentRowId, "documentRowId");
        PolicyChunk chunk = embedded.chunk();
        this.documentId = chunk.documentId();
        this.documentVersion = chunk.version();
        this.chunkId = chunk.chunkId();
        this.section = chunk.section();
        this.chunkIndex = chunk.chunkIndex();
        this.passage = chunk.passage();
        this.contentHash = chunk.contentHash();
        this.embeddingModel = embedded.embeddingModel();
        this.embedding = EmbeddingVectors.serialize(embedded.embedding());
    }

    @PrePersist
    void onCreate() {
        this.createdAt = now();
    }

    /** Rebuilds the domain chunk; citation metadata comes from the parent document row. */
    public PolicyChunk toChunk(PolicyDocumentEntity document) {
        return new PolicyChunk(chunkId, documentId, document.getTitle(), documentVersion,
                document.getEffectiveDate(), section, document.getOwner(), document.getClassification(),
                chunkIndex, passage);
    }

    public float[] embedding() {
        return EmbeddingVectors.deserialize(embedding);
    }

    public String getDocumentRowId() {
        return documentRowId;
    }

    public String getDocumentId() {
        return documentId;
    }

    public String getDocumentVersion() {
        return documentVersion;
    }

    public String getChunkId() {
        return chunkId;
    }

    public String getPassage() {
        return passage;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
