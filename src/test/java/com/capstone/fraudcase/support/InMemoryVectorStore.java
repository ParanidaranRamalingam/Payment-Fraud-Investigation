package com.capstone.fraudcase.support;

import com.capstone.fraudcase.knowledge.EmbeddedChunk;
import com.capstone.fraudcase.knowledge.EmbeddingVectors;
import com.capstone.fraudcase.knowledge.PolicyDocument;
import com.capstone.fraudcase.knowledge.PolicyVersionInfo;
import com.capstone.fraudcase.knowledge.ScoredChunk;
import com.capstone.fraudcase.knowledge.VectorStore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory {@link VectorStore} for tests: same contract as the JPA implementation (upsert replaces a
 * document version atomically; search ranks every stored chunk by cosine, regardless of document status).
 */
public class InMemoryVectorStore implements VectorStore {

    private record Entry(PolicyDocument document, List<EmbeddedChunk> chunks) {
    }

    private final Map<String, Entry> documents = new LinkedHashMap<>();

    private static String key(String documentId, String version) {
        return documentId + "@" + version;
    }

    @Override
    public void upsertDocument(PolicyDocument document, List<EmbeddedChunk> chunks) {
        documents.put(key(document.documentId(), document.version()),
                new Entry(document, List.copyOf(chunks)));
    }

    @Override
    public List<ScoredChunk> search(float[] queryEmbedding, int limit) {
        List<ScoredChunk> scored = new ArrayList<>();
        for (Entry entry : documents.values()) {
            for (EmbeddedChunk embedded : entry.chunks()) {
                scored.add(new ScoredChunk(embedded.chunk(), entry.document().status(),
                        EmbeddingVectors.cosine(queryEmbedding, embedded.embedding())));
            }
        }
        scored.sort(Comparator.comparingDouble(ScoredChunk::similarity).reversed());
        return scored.stream().limit(limit).toList();
    }

    @Override
    public List<PolicyVersionInfo> versions() {
        return documents.values().stream()
                .map(e -> new PolicyVersionInfo(e.document().documentId(), e.document().version(),
                        e.document().effectiveDate(), e.document().status()))
                .toList();
    }
}
