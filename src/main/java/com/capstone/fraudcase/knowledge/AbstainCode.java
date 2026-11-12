package com.capstone.fraudcase.knowledge;

/** Why a retrieval run produced no usable grounding. {@code null} on the outcome means grounded. */
public enum AbstainCode {
    /** The store held no candidates at all (empty corpus or no similarity above zero). */
    NO_MATCH,
    /** Candidates existed but none reached the similarity threshold. */
    BELOW_THRESHOLD,
    /** Candidates reached the threshold but none came from an approved, currently effective version. */
    NO_EFFECTIVE_VERSION,
    /** The vector store could not be searched; retrieval failed safe. */
    STORE_ERROR
}
