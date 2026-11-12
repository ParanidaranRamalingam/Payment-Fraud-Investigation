package com.capstone.fraudcase.knowledge;

import com.capstone.fraudcase.domain.DomainValidationException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Document-aware chunking: sections stay intact, sentences pack within the char bound, ids are stable. */
class PolicyChunkerTest {

    private static final PolicyProperties PROPS = new PolicyProperties(4, 0.72, 200, 8, false);

    private final PolicyChunker chunker = new PolicyChunker(PROPS);

    private static PolicyDocument doc(List<PolicyDocument.Section> sections) {
        return new PolicyDocument("pol-chunk", "Fraud Policy", "1.0", LocalDate.of(2026, 1, 1),
                "Fraud Operations", PolicyClassification.INTERNAL, PolicyStatus.APPROVED, sections);
    }

    @Test
    void chunks_neverMergeAcrossSections() {
        PolicyDocument document = doc(List.of(
                new PolicyDocument.Section("Scope", "Short scope text."),
                new PolicyDocument.Section("Procedure", "Short procedure text.")));

        List<PolicyChunk> chunks = chunker.chunk(document);

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).section()).isEqualTo("Scope");
        assertThat(chunks.get(0).passage()).isEqualTo("Short scope text.");
        assertThat(chunks.get(1).section()).isEqualTo("Procedure");
        assertThat(chunks.get(1).passage()).isEqualTo("Short procedure text.");
    }

    @Test
    void chunks_packSentencesUpToLimitAndKeepOrder() {
        String sentences = "First sentence is here and carries on for a while to fill space. Second one "
                + "follows it with yet more descriptive content attached. Third is a bit longer than the "
                + "others and pushes the boundary. Fourth closes out.";
        PolicyDocument document = doc(List.of(new PolicyDocument.Section("Procedure", sentences)));

        List<PolicyChunk> chunks = chunker.chunk(document);

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allSatisfy(c -> {
            assertThat(c.passage().length()).isLessThanOrEqualTo(200);
            assertThat(c.section()).isEqualTo("Procedure");
        });
        assertThat(String.join(" ", chunks.stream().map(PolicyChunk::passage).toList()))
                .contains("First sentence", "Fourth closes");
    }

    @Test
    void chunk_singleOversizedSentence_hardSplitAtLimit() {
        String longSentence = "word ".repeat(80);
        PolicyDocument document = doc(List.of(new PolicyDocument.Section("Scope", longSentence)));

        List<PolicyChunk> chunks = chunker.chunk(document);

        assertThat(chunks).hasSizeGreaterThan(1)
                .allSatisfy(c -> assertThat(c.passage().length()).isLessThanOrEqualTo(200));
    }

    @Test
    void chunkIds_deterministicDocumentVersionIndex() {
        PolicyDocument document = doc(List.of(
                new PolicyDocument.Section("Scope", "One."), new PolicyDocument.Section("Proc", "Two.")));

        assertThat(chunker.chunk(document)).extracting(PolicyChunk::chunkId)
                .containsExactly("pol-chunk:1.0:0", "pol-chunk:1.0:1");
    }

    @Test
    void chunks_carryFullCitationMetadata() {
        PolicyDocument document = doc(List.of(new PolicyDocument.Section("Scope", "Text.")));

        PolicyChunk chunk = chunker.chunk(document).getFirst();
        assertThat(chunk.documentId()).isEqualTo("pol-chunk");
        assertThat(chunk.title()).isEqualTo("Fraud Policy");
        assertThat(chunk.version()).isEqualTo("1.0");
        assertThat(chunk.effectiveDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(chunk.owner()).isEqualTo("Fraud Operations");
        assertThat(chunk.classification()).isEqualTo(PolicyClassification.INTERNAL);
        assertThat(chunk.chunkIndex()).isZero();
        assertThat(chunk.contentHash()).hasSize(64);
    }

    @Test
    void documentExceedingMaxChunks_rejected() {
        List<PolicyDocument.Section> sections = java.util.stream.IntStream.range(0, 10)
                .mapToObj(i -> new PolicyDocument.Section("S" + i, "Section " + i + " text."))
                .toList();

        assertThatThrownBy(() -> chunker.chunk(doc(sections)))
                .isInstanceOf(DomainValidationException.class).hasMessageContaining("chunks");
    }
}
