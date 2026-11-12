package com.capstone.fraudcase.knowledge;

/**
 * Produces dense, L2-normalized vectors for policy passages and retrieval queries. The model is an adapter
 * selected by configuration: the bundled implementation is a deterministic lexical hasher that runs fully
 * offline; a real embedding service can replace it without touching retrieval, thresholding or citations.
 * All vectors from one model share {@link #dimensions()}; {@link #modelId()} is recorded for provenance.
 */
public interface EmbeddingModel {

    /** L2-normalized embedding of {@code text}; a zero vector for text with no usable terms. */
    float[] embed(String text);

    int dimensions();

    /** Versioned identifier persisted alongside every stored embedding, e.g. {@code "lexical-hash:v1"}. */
    String modelId();
}
