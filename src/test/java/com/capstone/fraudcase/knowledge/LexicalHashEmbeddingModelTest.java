package com.capstone.fraudcase.knowledge;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class LexicalHashEmbeddingModelTest {

    private final LexicalHashEmbeddingModel model = new LexicalHashEmbeddingModel();

    @Test
    void embed_isDeterministic() {
        assertThat(model.embed("unauthorized transaction fraud")).isEqualTo(model.embed("unauthorized transaction fraud"));
    }

    @Test
    void embed_producesL2NormalizedVector() {
        float[] v = model.embed("unauthorized transaction fraud chargeback");
        double norm = 0;
        for (float x : v) {
            norm += x * x;
        }
        assertThat(norm).isCloseTo(1.0, within(1e-5));
    }

    @Test
    void embed_topicallySimilarTexts_scoreHighCosine() {
        double related = EmbeddingVectors.cosine(
                model.embed("unauthorized transaction reported by the cardholder, fraud investigation"),
                model.embed("cardholder reports an unrecognized charge, treat as fraud"));
        double unrelated = EmbeddingVectors.cosine(
                model.embed("unauthorized transaction reported by the cardholder"),
                model.embed("how to water indoor ferns weekly"));

        assertThat(related).isGreaterThan(0.7);
        assertThat(unrelated).isLessThan(related / 2);
    }

    @Test
    void embed_synonymParaphrase_landsNearOriginal() {
        double score = EmbeddingVectors.cosine(
                model.embed("duplicate charge posted twice"),
                model.embed("a repeated transaction appeared on the statement two times"));

        assertThat(score).isGreaterThan(0.5);
    }

    @Test
    void embed_emptyAndStopwordOnlyTexts_zeroVector() {
        assertThat(model.embed("")).containsOnly(0.0f);
        assertThat(model.embed("the and of to")).containsOnly(0.0f);
        assertThat(EmbeddingVectors.cosine(model.embed(""), model.embed("fraud"))).isZero();
    }

    @Test
    void serialize_deserialize_roundTripsWithinFloatPrecision() {
        float[] v = model.embed("merchant dispute refund evidence");
        float[] restored = EmbeddingVectors.deserialize(EmbeddingVectors.serialize(v));

        for (int i = 0; i < v.length; i++) {
            assertThat(restored[i]).isCloseTo(v[i], within(1e-5f));
        }
        assertThat(EmbeddingVectors.cosine(v, restored)).isGreaterThan(0.999);
    }
}
