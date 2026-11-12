package com.capstone.fraudcase.knowledge;

/**
 * One ranked candidate returned by {@link VectorStore#search}: the chunk, the lifecycle status of its
 * document version (so the retriever can explain why an unapproved passage was dropped) and the cosine
 * similarity in {@code [0, 1]}.
 */
public record ScoredChunk(PolicyChunk chunk, PolicyStatus documentStatus, double similarity) {

    public ScoredChunk {
        if (chunk == null || documentStatus == null) {
            throw new IllegalArgumentException("chunk and documentStatus are required");
        }
        if (Double.isNaN(similarity) || similarity < 0.0 || similarity > 1.0) {
            throw new IllegalArgumentException("similarity must be between 0 and 1");
        }
    }
}
