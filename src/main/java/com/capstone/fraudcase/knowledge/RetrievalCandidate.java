package com.capstone.fraudcase.knowledge;

/**
 * One candidate the retriever evaluated, with its final verdict. {@code included} candidates are the ones
 * supplied to the processor (and persisted as citations); every other candidate carries the reason it was
 * dropped so abstentions remain explainable in the retrieval trace.
 */
public record RetrievalCandidate(
        PolicyChunk chunk,
        PolicyStatus documentStatus,
        double similarity,
        boolean included,
        DropReason dropReason) {

    public enum DropReason {
        /** Similarity below the configured threshold. */
        BELOW_THRESHOLD,
        /** Document version is not APPROVED (draft or retired). */
        NOT_APPROVED,
        /** An older version superseded by the version effective as of the case date. */
        STALE_VERSION,
        /** A newer approved version whose effective date is still in the future. */
        NOT_YET_EFFECTIVE,
        /** Eligible but outside the configured top-k. */
        BEYOND_TOP_K
    }

    public RetrievalCandidate {
        if (included != (dropReason == null)) {
            throw new IllegalArgumentException("included candidates must have no dropReason and vice versa");
        }
    }

    static RetrievalCandidate kept(PolicyChunk chunk, PolicyStatus status, double similarity) {
        return new RetrievalCandidate(chunk, status, similarity, true, null);
    }

    static RetrievalCandidate dropped(PolicyChunk chunk, PolicyStatus status, double similarity,
                                      DropReason reason) {
        return new RetrievalCandidate(chunk, status, similarity, false, reason);
    }
}
