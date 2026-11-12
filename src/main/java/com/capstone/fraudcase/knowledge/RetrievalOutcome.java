package com.capstone.fraudcase.knowledge;

import com.capstone.fraudcase.domain.DomainValidationException;
import com.capstone.fraudcase.domain.PolicyCitation;

import java.util.List;

/**
 * Everything one retrieval run produced: the citations to offer the processor (empty on abstention), every
 * evaluated candidate with its verdict (the persisted retrieval trace), the configuration actually applied,
 * and provenance ({@code queryHash}, {@code embeddingModel}). The raw case-derived query text is never
 * retained here — only its hash and term count.
 */
public record RetrievalOutcome(
        List<PolicyCitation> citations,
        List<RetrievalCandidate> candidates,
        int topK,
        double similarityThreshold,
        AbstainCode abstainCode,
        String embeddingModel,
        String queryHash,
        int queryTermCount) {

    public RetrievalOutcome {
        citations = List.copyOf(citations);
        candidates = List.copyOf(candidates);
        if (citations.isEmpty() != (abstainCode != null)) {
            throw new DomainValidationException(
                    "an abstaining outcome must carry an abstainCode; a grounded outcome must not");
        }
        if (citations.size() > topK) {
            throw new DomainValidationException("citations must not exceed topK");
        }
    }

    /** True when at least one approved passage met the threshold and was supplied. */
    public boolean grounded() {
        return !citations.isEmpty();
    }

    /**
     * Missing-information entry describing the absent policy evidence. Only meaningful on abstention;
     * the caller must check {@link #grounded()} first.
     */
    public String missingPolicyDescription() {
        return "approved policy passage at or above the similarity threshold (retrieval abstained: "
                + abstainCode + ")";
    }
}
