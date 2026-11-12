package com.capstone.fraudcase.knowledge;

import java.util.List;

/**
 * Persistence-backed store for policy embeddings. Implementations may be a MySQL table scanned in memory
 * (the bundled default, adequate for the capstone corpus) or a dedicated ANN index; the retrieval contract
 * stays the same: ranked candidates with full citation metadata, ordered by descending similarity.
 *
 * <p>The store returns every candidate it evaluated, including passages from documents that are not
 * {@link PolicyStatus#APPROVED}; the retriever owns approval, threshold and effective-version policy so a
 * store swap cannot weaken the safety rules.
 */
public interface VectorStore {

    /**
     * Atomically replaces the given document version: removes any existing rows (and their chunks) for
     * {@code (documentId, version)} and inserts the document plus all supplied embedded chunks.
     */
    void upsertDocument(PolicyDocument document, List<EmbeddedChunk> chunks);

    /** Up to {@code limit} candidates ordered by descending cosine similarity to the query vector. */
    List<ScoredChunk> search(float[] queryEmbedding, int limit);

    /** Version metadata of every stored document (all statuses), for effective-version resolution. */
    List<PolicyVersionInfo> versions();
}
