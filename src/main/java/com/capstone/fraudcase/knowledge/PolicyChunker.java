package com.capstone.fraudcase.knowledge;

import com.capstone.fraudcase.domain.DomainValidationException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Document-aware chunking. A section is never merged with another section, so every chunk keeps an exact
 * {@code section} label for citation. Within a section, text is split at sentence boundaries and packed into
 * passages of at most {@code fraud.policy.max-chunk-chars}; a single sentence longer than the limit is split
 * hard at the limit so no chunk is silently dropped. Chunk ids are deterministic
 * ({@code documentId:version:index}) so re-ingesting identical content reproduces identical citations.
 */
@Component
public class PolicyChunker {

    private static final Pattern SENTENCE_BOUNDARY = Pattern.compile("(?<=[.!?;])\\s+|\\n+");

    private final int maxChars;
    private final int maxChunks;

    public PolicyChunker(PolicyProperties properties) {
        this.maxChars = properties.maxChunkChars();
        this.maxChunks = properties.maxChunksPerDocument();
    }

    public List<PolicyChunk> chunk(PolicyDocument document) {
        List<PolicyChunk> chunks = new ArrayList<>();
        int index = 0;
        for (PolicyDocument.Section section : document.sections()) {
            for (String passage : splitSection(section.text())) {
                if (chunks.size() >= maxChunks) {
                    throw new DomainValidationException("document exceeds the maximum of "
                            + maxChunks + " chunks; split it into smaller documents");
                }
                chunks.add(new PolicyChunk(chunkId(document, index), document.documentId(), document.title(),
                        document.version(), document.effectiveDate(), section.heading(), document.owner(),
                        document.classification(), index, passage));
                index++;
            }
        }
        return List.copyOf(chunks);
    }

    static String chunkId(PolicyDocument document, int index) {
        return document.documentId() + ":" + document.version() + ":" + index;
    }

    private List<String> splitSection(String text) {
        String normalized = text.strip();
        List<String> pieces = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String sentence : SENTENCE_BOUNDARY.split(normalized)) {
            String candidate = current.isEmpty() ? sentence : current + " " + sentence;
            if (candidate.length() <= maxChars) {
                current = new StringBuilder(candidate);
                continue;
            }
            if (!current.isEmpty()) {
                pieces.add(current.toString());
                current = new StringBuilder();
            }
            while (sentence.length() > maxChars) {
                pieces.add(sentence.substring(0, maxChars));
                sentence = sentence.substring(maxChars).stripLeading();
            }
            if (!sentence.isEmpty()) {
                current = new StringBuilder(sentence);
            }
        }
        if (!current.isEmpty()) {
            pieces.add(current.toString());
        }
        return pieces;
    }
}
