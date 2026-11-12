package com.capstone.fraudcase.knowledge;

import com.capstone.fraudcase.domain.ClaimType;
import com.capstone.fraudcase.domain.PolicyCitation;
import com.capstone.fraudcase.domain.TransactionClaim;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Case-aware policy retrieval. Pipeline per {@code docs/RAG_POLICY.md}: embed a deterministic query built
 * from the claim and statement -> configurable top-k search -> keep candidates at or above the similarity
 * threshold -> keep only the currently effective approved version per document -> map the survivors to exact
 * {@link PolicyCitation}s.
 *
 * <p><b>Abstention contract:</b> when no approved, effective passage reaches the threshold the outcome
 * carries no citations and an explicit {@link AbstainCode}; callers must surface the missing policy evidence
 * and route safely to manual review rather than inventing procedure. A store failure abstains the same way
 * ({@link AbstainCode#STORE_ERROR}) — retrieval problems never fabricate grounding and never propagate.
 *
 * <p>Retrieved passages are untrusted data. They are quoted verbatim into citations; nothing in this class
 * executes, follows or validates instructions contained in them.
 */
@Component
public class PolicyRetriever {

    /** Claim-type vocabulary mixed into every query so retrieval is case-aware without any model call. */
    private static final Map<ClaimType, String> CLAIM_TYPE_TERMS = Map.of(
            ClaimType.UNRECOGNISED_TRANSACTION,
            "unauthorized transaction fraud cardholder report chargeback",
            ClaimType.MERCHANT_ISSUE,
            "merchant dispute goods services refund cancellation chargeback",
            ClaimType.DUPLICATE_CHARGE,
            "duplicate transaction posting reversal correction",
            ClaimType.OTHER,
            "transaction dispute investigation review");

    private final VectorStore store;
    private final EmbeddingModel embedding;
    private final PolicyProperties properties;

    public PolicyRetriever(VectorStore store, EmbeddingModel embedding, PolicyProperties properties) {
        this.store = store;
        this.embedding = embedding;
        this.properties = properties;
    }

    public RetrievalOutcome retrieve(String caseId, TransactionClaim claim, String customerStatement,
                                     LocalDate asOf) {
        Objects.requireNonNull(caseId, "caseId");
        Objects.requireNonNull(asOf, "asOf");
        String query = queryText(claim, customerStatement);
        int topK = properties.topK();
        double threshold = properties.similarityThreshold();

        List<ScoredChunk> searched;
        List<PolicyVersionInfo> versions;
        try {
            searched = store.search(embedding.embed(query), Math.max(topK * 4, topK));
            versions = store.versions();
        } catch (RuntimeException e) {
            return outcome(query, List.of(), List.of(), topK, threshold, AbstainCode.STORE_ERROR);
        }
        Map<String, String> effectiveVersion = effectiveVersions(versions, asOf);

        List<RetrievalCandidate> candidates = new ArrayList<>();
        List<ScoredChunk> eligible = new ArrayList<>();
        for (ScoredChunk hit : searched) {
            RetrievalCandidate.DropReason drop = verdict(hit, effectiveVersion, threshold, asOf);
            if (drop == null) {
                eligible.add(hit);
            }
            candidates.add(drop == null
                    ? RetrievalCandidate.kept(hit.chunk(), hit.documentStatus(), hit.similarity())
                    : RetrievalCandidate.dropped(hit.chunk(), hit.documentStatus(), hit.similarity(), drop));
        }
        List<ScoredChunk> kept = eligible.stream().limit(topK).toList();
        for (int i = 0; i < candidates.size(); i++) {
            RetrievalCandidate c = candidates.get(i);
            if (c.included() && !kept.stream().anyMatch(k -> k.chunk().chunkId().equals(c.chunk().chunkId()))) {
                candidates.set(i, RetrievalCandidate.dropped(c.chunk(), c.documentStatus(), c.similarity(),
                        RetrievalCandidate.DropReason.BEYOND_TOP_K));
            }
        }
        List<PolicyCitation> citations = kept.stream()
                .map(hit -> toCitation(hit.chunk(), hit.similarity()))
                .toList();
        return outcome(query, citations, candidates, topK, threshold,
                citations.isEmpty() ? abstainCode(candidates, threshold) : null);
    }

    /** Deterministic query from the claim type, merchant and the (untrusted) customer statement. */
    static String queryText(TransactionClaim claim, String customerStatement) {
        StringBuilder q = new StringBuilder();
        if (claim != null) {
            q.append(CLAIM_TYPE_TERMS.getOrDefault(claim.claimType(), "transaction dispute"));
            q.append(' ').append(claim.merchantName());
        }
        if (customerStatement != null) {
            if (!q.isEmpty()) {
                q.append(' ');
            }
            q.append(customerStatement);
        }
        return q.toString();
    }

    private static RetrievalCandidate.DropReason verdict(ScoredChunk hit, Map<String, String> effectiveVersion,
                                                         double threshold, LocalDate asOf) {
        if (hit.documentStatus() != PolicyStatus.APPROVED) {
            return RetrievalCandidate.DropReason.NOT_APPROVED;
        }
        if (hit.similarity() < threshold) {
            return RetrievalCandidate.DropReason.BELOW_THRESHOLD;
        }
        if (hit.chunk().effectiveDate().isAfter(asOf)) {
            return RetrievalCandidate.DropReason.NOT_YET_EFFECTIVE;
        }
        String effective = effectiveVersion.get(hit.chunk().documentId());
        if (effective == null || !effective.equals(hit.chunk().version())) {
            return RetrievalCandidate.DropReason.STALE_VERSION;
        }
        return null;
    }

    /**
     * For each document, the approved version with the latest effective date on or before {@code asOf}.
     * Documents with no approved version effective yet (or only future-dated ones) have no entry, so their
     * chunks are always dropped as stale.
     */
    static Map<String, String> effectiveVersions(List<PolicyVersionInfo> versions, LocalDate asOf) {
        Map<String, List<PolicyVersionInfo>> byDocument = new HashMap<>();
        for (PolicyVersionInfo v : versions) {
            byDocument.computeIfAbsent(v.documentId(), k -> new ArrayList<>()).add(v);
        }
        Map<String, String> effective = new HashMap<>();
        byDocument.forEach((documentId, list) -> list.stream()
                .filter(v -> v.status() == PolicyStatus.APPROVED && !v.effectiveDate().isAfter(asOf))
                .max(Comparator.comparing(PolicyVersionInfo::effectiveDate)
                        .thenComparing(PolicyVersionInfo::version))
                .ifPresent(v -> effective.put(documentId, v.version())));
        return effective;
    }

    private static AbstainCode abstainCode(List<RetrievalCandidate> candidates, double threshold) {
        if (candidates.isEmpty()) {
            return AbstainCode.NO_MATCH;
        }
        if (candidates.stream().noneMatch(c -> c.similarity() >= threshold)) {
            return AbstainCode.BELOW_THRESHOLD;
        }
        return AbstainCode.NO_EFFECTIVE_VERSION;
    }

    private static PolicyCitation toCitation(PolicyChunk chunk, double similarity) {
        double rounded = BigDecimal.valueOf(similarity).setScale(4, RoundingMode.HALF_UP).doubleValue();
        return new PolicyCitation(chunk.documentId(), chunk.title(), chunk.version(), chunk.effectiveDate(),
                chunk.section(), chunk.owner(), chunk.chunkId(), rounded, chunk.passage());
    }

    private RetrievalOutcome outcome(String query, List<PolicyCitation> citations,
                                     List<RetrievalCandidate> candidates, int topK, double threshold,
                                     AbstainCode abstainCode) {
        return new RetrievalOutcome(citations, candidates, topK, threshold, abstainCode,
                embedding.modelId(), KnowledgeHashes.sha256(query), termCount(query));
    }

    private static int termCount(String query) {
        String trimmed = query.strip();
        return trimmed.isEmpty() ? 0 : trimmed.split("\\s+").length;
    }
}
