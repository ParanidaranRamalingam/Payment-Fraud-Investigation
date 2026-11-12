package com.capstone.fraudcase.persistence.entity;

import com.capstone.fraudcase.knowledge.RetrievalCandidate;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** One ranked candidate row inside {@link PolicyRetrievalTraceEntity#getHits()}. */
@Embeddable
public class PolicyRetrievalHit {

    @Column(name = "chunk_id", length = 64, nullable = false)
    private String chunkId;

    @Column(name = "document_id", length = 64, nullable = false)
    private String documentId;

    @Column(name = "document_version", length = 32, nullable = false)
    private String documentVersion;

    @Column(name = "similarity", precision = 5, scale = 4, nullable = false)
    private BigDecimal similarity;

    @Column(name = "included", nullable = false)
    private boolean included;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "drop_reason", length = 32)
    private RetrievalCandidate.DropReason dropReason;

    protected PolicyRetrievalHit() {
    }

    public PolicyRetrievalHit(RetrievalCandidate candidate) {
        this.chunkId = candidate.chunk().chunkId();
        this.documentId = candidate.chunk().documentId();
        this.documentVersion = candidate.chunk().version();
        this.similarity = BigDecimal.valueOf(candidate.similarity()).setScale(4, RoundingMode.HALF_UP);
        this.included = candidate.included();
        this.dropReason = candidate.dropReason();
    }

    public String getChunkId() {
        return chunkId;
    }

    public boolean isIncluded() {
        return included;
    }
}
