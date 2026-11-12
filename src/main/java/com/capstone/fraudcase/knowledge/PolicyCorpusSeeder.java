package com.capstone.fraudcase.knowledge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Seeds the bundled synthetic corpus on startup when {@code fraud.policy.seed-sample-corpus=true} (the
 * default) and the store is empty. Idempotent: a store that already holds any document version is left
 * untouched. A seeding failure degrades to the safe state (empty corpus -> every retrieval abstains)
 * rather than failing startup.
 */
@Component
public class PolicyCorpusSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PolicyCorpusSeeder.class);

    private final PolicyIngestionService ingestion;
    private final VectorStore store;
    private final PolicyProperties properties;

    public PolicyCorpusSeeder(PolicyIngestionService ingestion, VectorStore store,
                              PolicyProperties properties) {
        this.ingestion = ingestion;
        this.store = store;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.seedSampleCorpus()) {
            return;
        }
        try {
            if (!store.versions().isEmpty()) {
                return;
            }
            SamplePolicyCorpus.documents().forEach(ingestion::ingest);
            log.info("Seeded {} sample policy document versions", SamplePolicyCorpus.documents().size());
        } catch (RuntimeException e) {
            log.warn("Sample policy corpus could not be seeded; retrievals will abstain: {}",
                    e.getMessage());
        }
    }
}
