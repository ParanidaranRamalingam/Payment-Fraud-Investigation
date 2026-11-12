package com.capstone.fraudcase.knowledge;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * Ingestion pipeline: validate ({@link PolicyDocument} enforces its metadata contract at construction) ->
 * document-aware chunking -> embed each chunk with document/section context -> store. Re-ingesting an
 * existing {@code (documentId, version)} replaces it atomically, so corrections never leave stale chunks
 * behind. Only passages that survive validation reach the store; content itself is untrusted data and is
 * stored verbatim, never interpreted.
 */
@Service
public class PolicyIngestionService {

    private final PolicyChunker chunker;
    private final EmbeddingModel embeddingModel;
    private final VectorStore vectorStore;

    public PolicyIngestionService(PolicyChunker chunker, EmbeddingModel embeddingModel,
                                  VectorStore vectorStore) {
        this.chunker = chunker;
        this.embeddingModel = embeddingModel;
        this.vectorStore = vectorStore;
    }

    @Transactional
    public IngestionSummary ingest(PolicyDocument document) {
        Objects.requireNonNull(document, "document");
        List<PolicyChunk> chunks = chunker.chunk(document);
        List<EmbeddedChunk> embedded = chunks.stream()
                .map(chunk -> new EmbeddedChunk(chunk, embeddingModel.embed(chunk.embeddingInput()),
                        embeddingModel.modelId()))
                .toList();
        vectorStore.upsertDocument(document, embedded);
        return new IngestionSummary(document.documentId(), document.version(), chunks.size(),
                document.contentHash());
    }

    public record IngestionSummary(String documentId, String version, int chunkCount, String contentHash) {
    }
}
