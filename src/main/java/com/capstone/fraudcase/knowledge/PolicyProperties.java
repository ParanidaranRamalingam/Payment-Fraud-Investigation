package com.capstone.fraudcase.knowledge;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code fraud.policy.*} retrieval and ingestion tuning. The two safety-critical knobs required by the RAG
 * policy are {@code top-k} (passages supplied to the processor, default 4) and
 * {@code similarity-threshold} (minimum cosine score for a passage to count as approved grounding,
 * default 0.72). Chunking bounds and the sample-corpus seeder are also configurable.
 */
@ConfigurationProperties("fraud.policy")
public record PolicyProperties(
        @DefaultValue("4") int topK,
        @DefaultValue("0.72") double similarityThreshold,
        @DefaultValue("800") int maxChunkChars,
        @DefaultValue("64") int maxChunksPerDocument,
        @DefaultValue("true") boolean seedSampleCorpus) {

    public PolicyProperties {
        if (topK < 1 || topK > 32) {
            throw new IllegalArgumentException("fraud.policy.top-k must be between 1 and 32");
        }
        if (Double.isNaN(similarityThreshold) || similarityThreshold < 0.0 || similarityThreshold > 1.0) {
            throw new IllegalArgumentException("fraud.policy.similarity-threshold must be between 0 and 1");
        }
        if (maxChunkChars < 200 || maxChunkChars > PolicyChunk.MAX_PASSAGE) {
            throw new IllegalArgumentException(
                    "fraud.policy.max-chunk-chars must be between 200 and " + PolicyChunk.MAX_PASSAGE);
        }
        if (maxChunksPerDocument < 1 || maxChunksPerDocument > 256) {
            throw new IllegalArgumentException("fraud.policy.max-chunks-per-document must be between 1 and 256");
        }
    }
}
