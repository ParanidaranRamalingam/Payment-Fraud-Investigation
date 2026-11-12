package com.capstone.fraudcase.knowledge;

import com.capstone.fraudcase.persistence.entity.PolicyChunkEntity;
import com.capstone.fraudcase.persistence.entity.PolicyDocumentEntity;
import com.capstone.fraudcase.persistence.repository.PolicyChunkRepository;
import com.capstone.fraudcase.persistence.repository.PolicyDocumentRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * MySQL-backed {@link VectorStore}: chunks and document versions live in {@code policy_chunk}/
 * {@code policy_document}; cosine similarity is computed in memory after loading the corpus. The capstone
 * corpus is small (tens of chunks), so a full scan is exact and dependency-free — a dedicated ANN index can
 * replace this class without changing the retrieval contract.
 */
@Component
public class JpaVectorStore implements VectorStore {

    private final PolicyDocumentRepository documents;
    private final PolicyChunkRepository chunks;

    public JpaVectorStore(PolicyDocumentRepository documents, PolicyChunkRepository chunks) {
        this.documents = documents;
        this.chunks = chunks;
    }

    @Override
    @Transactional
    public void upsertDocument(PolicyDocument document, List<EmbeddedChunk> embedded) {
        documents.findByDocumentIdAndVersion(document.documentId(), document.version())
                .ifPresent(existing -> {
                    chunks.deleteByDocumentRowId(existing.getId());
                    documents.delete(existing);
                    documents.flush();
                });
        PolicyDocumentEntity saved = documents.save(new PolicyDocumentEntity(document));
        chunks.saveAll(embedded.stream().map(e -> new PolicyChunkEntity(saved.getId(), e)).toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ScoredChunk> search(float[] queryEmbedding, int limit) {
        Map<String, PolicyDocumentEntity> docsById = documents.findAll().stream()
                .collect(Collectors.toMap(PolicyDocumentEntity::getId, Function.identity()));
        return chunks.findAll().stream()
                .filter(chunk -> docsById.containsKey(chunk.getDocumentRowId()))
                .map(chunk -> new ScoredChunk(
                        chunk.toChunk(docsById.get(chunk.getDocumentRowId())),
                        docsById.get(chunk.getDocumentRowId()).getStatus(),
                        EmbeddingVectors.cosine(queryEmbedding, chunk.embedding())))
                .sorted(Comparator.comparingDouble(ScoredChunk::similarity).reversed())
                .limit(limit)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PolicyVersionInfo> versions() {
        return documents.findAll().stream().map(PolicyDocumentEntity::toVersionInfo).toList();
    }
}
