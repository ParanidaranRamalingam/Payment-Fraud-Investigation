package com.capstone.fraudcase.knowledge;

import com.capstone.fraudcase.domain.DomainValidationException;
import com.capstone.fraudcase.support.InMemoryVectorStore;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Ingestion pipeline: validate -> chunk -> embed -> store, with replace semantics on re-ingest. */
class PolicyIngestionServiceTest {

    private static final PolicyProperties PROPS = new PolicyProperties(4, 0.72, 400, 64, false);

    private final InMemoryVectorStore store = new InMemoryVectorStore();
    private final LexicalHashEmbeddingModel embedder = new LexicalHashEmbeddingModel();
    private final PolicyIngestionService service =
            new PolicyIngestionService(new PolicyChunker(PROPS), embedder, store);

    private static PolicyDocument doc(String text) {
        return new PolicyDocument("pol-ingest", "Fraud Policy", "1.0", LocalDate.of(2026, 1, 1),
                "Fraud Operations", PolicyClassification.INTERNAL, PolicyStatus.APPROVED,
                List.of(new PolicyDocument.Section("Procedure", text)));
    }

    @Test
    void ingest_storesDocumentAndEmbeddedChunks() {
        PolicyIngestionService.IngestionSummary summary = service.ingest(doc(
                "First procedure sentence. Second procedure sentence follows."));

        assertThat(summary.chunkCount()).isGreaterThanOrEqualTo(1);
        assertThat(summary.contentHash()).hasSize(64);
        assertThat(store.versions()).singleElement().satisfies(v -> {
            assertThat(v.documentId()).isEqualTo("pol-ingest");
            assertThat(v.version()).isEqualTo("1.0");
            assertThat(v.status()).isEqualTo(PolicyStatus.APPROVED);
        });
        List<ScoredChunk> hits = store.search(embedder.embed("procedure"), 10);
        assertThat(hits).isNotEmpty();
        assertThat(hits.getFirst().similarity()).isGreaterThan(0.0);
        assertThat(hits.getFirst().chunk().chunkId()).startsWith("pol-ingest:1.0:");
        assertThat(hits.getFirst().documentStatus()).isEqualTo(PolicyStatus.APPROVED);
    }

    @Test
    void reingestSameVersion_replacesChunksAtomically() {
        service.ingest(doc("First version content."));
        int firstCount = store.search(embedder.embed("content"), 50).size();

        service.ingest(doc("Replacement content only."));
        List<ScoredChunk> hits = store.search(embedder.embed("content"), 50);

        assertThat(store.versions()).hasSize(1);
        assertThat(hits.size()).isEqualTo(firstCount);
        assertThat(hits).allSatisfy(h -> assertThat(h.chunk().passage()).contains("Replacement"));
    }

    @Test
    void separateVersions_coexist() {
        service.ingest(doc("Version one text."));
        service.ingest(new PolicyDocument("pol-ingest", "Fraud Policy", "2.0", LocalDate.of(2026, 6, 1),
                "Fraud Operations", PolicyClassification.INTERNAL, PolicyStatus.APPROVED,
                List.of(new PolicyDocument.Section("Procedure", "Version two text."))));

        assertThat(store.versions()).extracting(PolicyVersionInfo::version)
                .containsExactlyInAnyOrder("1.0", "2.0");
    }

    @Test
    void invalidDocument_rejectedBeforeStoreWrite() {
        assertThatThrownBy(() -> service.ingest(new PolicyDocument("BAD ID", "T", "1.0",
                LocalDate.now(), "owner", PolicyClassification.INTERNAL, PolicyStatus.APPROVED,
                List.of(new PolicyDocument.Section("S", "text")))))
                .isInstanceOf(DomainValidationException.class);
        assertThat(store.versions()).isEmpty();
    }
}
