package com.capstone.fraudcase.knowledge;

import com.capstone.fraudcase.domain.DomainValidationException;
import com.capstone.fraudcase.domain.SensitiveDataGuard;

import java.time.LocalDate;

/**
 * A retrievable unit of policy text. Every chunk carries the complete citation metadata of its source
 * document version ({@code documentId}, {@code title}, {@code version}, {@code effectiveDate},
 * {@code section}, {@code owner}) plus its own {@code chunkId}, {@code chunkIndex} and {@code passage}, so a
 * chunk alone is enough to render an exact citation. Passage content is untrusted data: it is quoted, never
 * interpreted as instructions.
 */
public record PolicyChunk(
        String chunkId,
        String documentId,
        String title,
        String version,
        LocalDate effectiveDate,
        String section,
        String owner,
        PolicyClassification classification,
        int chunkIndex,
        String passage) {

    public static final int MAX_CHUNK_ID = 64;
    public static final int MAX_PASSAGE = 4000;

    public PolicyChunk {
        require(chunkId, "chunkId", MAX_CHUNK_ID);
        require(documentId, "documentId", 64);
        require(title, "title", 200);
        require(version, "version", 32);
        if (effectiveDate == null) {
            throw new DomainValidationException("effectiveDate is required");
        }
        require(section, "section", 120);
        require(owner, "owner", 120);
        if (classification == null) {
            throw new DomainValidationException("classification is required");
        }
        if (chunkIndex < 0) {
            throw new DomainValidationException("chunkIndex must not be negative");
        }
        require(passage, "passage", MAX_PASSAGE);
        if (!SensitiveDataGuard.isSafeFreeText(passage)) {
            throw new DomainValidationException("passage contains unsafe content");
        }
    }

    /** Provenance hash binding this chunk id to its exact passage text. */
    public String contentHash() {
        return KnowledgeHashes.sha256(chunkId + "\n" + passage);
    }

    /** Text actually embedded: document and section context plus the passage itself. */
    public String embeddingInput() {
        return title + "\n" + section + "\n" + passage;
    }

    private static void require(String value, String field, int max) {
        if (value == null || value.isBlank()) {
            throw new DomainValidationException(field + " is required");
        }
        if (value.length() > max) {
            throw new DomainValidationException(field + " must be at most " + max + " characters");
        }
    }
}
