package com.capstone.fraudcase.knowledge;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Deterministic, offline default for {@link EmbeddingModel}. Text is normalized (lowercase, stopwords and
 * short tokens dropped, domain synonyms canonicalized) and projected onto a fixed vector space: a curated
 * fraud/dispute vocabulary occupies the first {@code VOCABULARY.size()} dimensions at full weight, and all
 * remaining terms are feature-hashed into the residual dimensions at reduced weight. Two passages about the
 * same subject share canonical vocabulary coordinates and therefore score high on cosine similarity, while
 * unrelated text lands in scattered residual coordinates. Identical input always produces an identical vector.
 */
@Component
public class LexicalHashEmbeddingModel implements EmbeddingModel {

    public static final String MODEL_ID = "lexical-hash:v1";
    public static final int DIMENSIONS = 384;

    private static final float RESIDUAL_WEIGHT = 0.15f;

    private static final Set<String> STOPWORDS = Set.of(
            "the", "and", "for", "are", "was", "were", "with", "without", "that", "this", "these", "those",
            "from", "into", "onto", "upon", "have", "has", "had", "been", "being", "will", "would", "shall",
            "should", "can", "could", "may", "might", "must", "not", "all", "any", "each", "both", "such",
            "than", "then", "when", "where", "which", "while", "who", "whom", "whose", "what", "how", "why",
            "you", "your", "yours", "they", "them", "their", "theirs", "our", "ours", "its", "his", "her",
            "hers", "him", "she", "out", "about", "above", "below", "between", "under", "over",
            "after", "before", "during", "within", "against", "because", "through", "per", "via", "etc",
            "does", "doing", "did", "done", "make", "made", "see", "also", "only", "very", "just", "more",
            "most", "less", "least", "own", "same", "other", "another", "some", "none", "here",
            "there", "now", "once", "ever", "never", "always", "often", "please", "note", "yes");

    private static final Map<String, String> SYNONYMS = Map.ofEntries(
            Map.entry("unauthorised", "unauthorized"), Map.entry("unrecognised", "unrecognized"),
            Map.entry("unrecognized", "unauthorized"), Map.entry("unfamiliar", "unauthorized"),
            Map.entry("recognise", "unrecognized"), Map.entry("recognize", "unrecognized"),
            Map.entry("fraudulent", "fraud"), Map.entry("scam", "fraud"), Map.entry("stolen", "compromised"),
            Map.entry("retailer", "merchant"), Map.entry("seller", "merchant"), Map.entry("vendor", "merchant"),
            Map.entry("reimbursement", "refund"), Map.entry("reimburse", "refund"),
            Map.entry("repeated", "duplicate"), Map.entry("double", "duplicate"),
            Map.entry("cancelled", "cancellation"), Map.entry("cancel", "cancellation"),
            Map.entry("item", "goods"), Map.entry("product", "goods"), Map.entry("merchandise", "goods"),
            Map.entry("notify", "notification"), Map.entry("inform", "notification"),
            Map.entry("proof", "evidence"), Map.entry("documentation", "evidence"),
            Map.entry("receipt", "evidence"), Map.entry("purchase", "transaction"),
            Map.entry("buy", "transaction"), Map.entry("bought", "transaction"),
            Map.entry("payment", "transaction"), Map.entry("charge", "transaction"),
            Map.entry("billing", "transaction"),
            Map.entry("query", "dispute"), Map.entry("complaint", "dispute"),
            Map.entry("escalate", "escalation"), Map.entry("agent", "analyst"),
            Map.entry("customer", "cardholder"), Map.entry("client", "cardholder"),
            Map.entry("deadline", "timeline"), Map.entry("days", "timeline"));

    private static final List<String> VOCABULARY = List.of(
            "unauthorized", "fraud", "transaction", "chargeback", "dispute", "merchant", "duplicate",
            "refund", "report", "timeline", "escalation", "review", "evidence", "device", "location",
            "travel", "delivery", "goods", "services", "subscription", "recurring", "cancellation", "card",
            "provisional", "credit", "liability", "compromised", "pin", "contactless", "atm", "withdrawal",
            "velocity", "counterfeit", "skimming", "phishing", "credential", "account", "takeover",
            "notification", "alert", "cardholder", "statement", "amount", "currency", "investigation",
            "manual", "analyst", "approve", "decline", "reversal", "settlement", "descriptor",
            "authorization", "secure", "otp", "cvv", "kyc", "verification", "identity", "claim",
            "risk", "score", "threshold", "regulation", "compliance", "network", "issuer", "acquirer",
            "posting", "ledger", "balance", "partial", "defective", "mule", "onboarding",
            "screening", "watchlist", "sanctions", "chargeoff", "writeoff", "aging", "sla");

    private static final Map<String, Integer> VOCAB_INDEX = indexVocabulary();

    private static Map<String, Integer> indexVocabulary() {
        java.util.Map<String, Integer> map = new java.util.HashMap<>();
        for (int i = 0; i < VOCABULARY.size(); i++) {
            map.put(VOCABULARY.get(i), i);
        }
        return Map.copyOf(map);
    }

    @Override
    public float[] embed(String text) {
        float[] vector = new float[DIMENSIONS];
        if (text != null) {
            for (String token : tokens(text)) {
                String canonical = SYNONYMS.getOrDefault(token, token);
                Integer index = VOCAB_INDEX.get(canonical);
                if (index != null) {
                    vector[index] += 1.0f;
                } else {
                    vector[VOCABULARY.size() + (int) (fnv1a(canonical) % (DIMENSIONS - VOCABULARY.size()))]
                            += RESIDUAL_WEIGHT;
                }
            }
        }
        return normalize(vector);
    }

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }

    @Override
    public String modelId() {
        return MODEL_ID;
    }

    static List<String> tokens(String text) {
        return java.util.Arrays.stream(text.toLowerCase(java.util.Locale.ROOT).split("[^a-z0-9]+"))
                .filter(t -> t.length() >= 3 && !STOPWORDS.contains(t))
                .map(LexicalHashEmbeddingModel::stem)
                .filter(t -> t.length() >= 3)
                .toList();
    }

    private static String stem(String token) {
        if (token.length() > 4 && token.endsWith("ies")) {
            return token.substring(0, token.length() - 3) + "y";
        }
        if (token.length() > 4 && token.endsWith("es")) {
            return token.substring(0, token.length() - 2);
        }
        if (token.length() > 3 && token.endsWith("s") && !token.endsWith("ss")) {
            return token.substring(0, token.length() - 1);
        }
        return token;
    }

    private static long fnv1a(String value) {
        long hash = 0xcbf29ce484222325L;
        for (int i = 0; i < value.length(); i++) {
            hash ^= value.charAt(i);
            hash = (hash * 0x100000001b3L) & 0x7fffffffffffffffL;
        }
        return hash;
    }

    private static float[] normalize(float[] vector) {
        double sum = 0.0;
        for (float v : vector) {
            sum += v * v;
        }
        if (sum == 0.0) {
            return vector;
        }
        float norm = (float) Math.sqrt(sum);
        for (int i = 0; i < vector.length; i++) {
            vector[i] /= norm;
        }
        return vector;
    }
}
