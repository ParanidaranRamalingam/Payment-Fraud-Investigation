package com.capstone.fraudcase.knowledge;

import java.util.Arrays;
import java.util.Objects;

/** A chunk together with the embedding that will be stored for similarity search. */
public record EmbeddedChunk(PolicyChunk chunk, float[] embedding, String embeddingModel) {

    public EmbeddedChunk {
        Objects.requireNonNull(chunk, "chunk");
        Objects.requireNonNull(embedding, "embedding");
        Objects.requireNonNull(embeddingModel, "embeddingModel");
        embedding = Arrays.copyOf(embedding, embedding.length);
    }

    @Override
    public float[] embedding() {
        return Arrays.copyOf(embedding, embedding.length);
    }
}
