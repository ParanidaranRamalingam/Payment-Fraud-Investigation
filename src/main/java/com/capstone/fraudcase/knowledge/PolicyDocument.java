package com.capstone.fraudcase.knowledge;

import com.capstone.fraudcase.domain.DomainValidationException;
import com.capstone.fraudcase.domain.SensitiveDataGuard;

import java.time.LocalDate;
import java.util.List;
import java.util.regex.Pattern;

/**
 * One policy document version submitted for ingestion. Metadata is validated at construction so a malformed
 * document can never reach the vector store. {@code documentId}/{@code version} are bounded so that derived
 * chunk ids ({@code documentId:version:index}) always fit the 64-character chunk-id column. Section text is
 * untrusted data: it is bounded and rejected only when it carries control characters or what looks like an
 * unmasked account number; instruction-like text is stored verbatim and never executed.
 */
public record PolicyDocument(
        String documentId,
        String title,
        String version,
        LocalDate effectiveDate,
        String owner,
        PolicyClassification classification,
        PolicyStatus status,
        List<Section> sections) {

    public record Section(String heading, String text) {
        public Section {
            require(heading, "section.heading", MAX_SECTION_HEADING);
            require(text, "section.text", MAX_SECTION_CHARS);
            if (!SensitiveDataGuard.isSafeFreeText(text)) {
                throw new DomainValidationException("section.text contains unsafe content");
            }
        }
    }

    private static final Pattern DOCUMENT_ID = Pattern.compile("^[a-z0-9][a-z0-9._-]{0,47}$");
    private static final Pattern VERSION = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{0,30}$");
    private static final int MAX_TITLE = 200;
    private static final int MAX_OWNER = 120;
    private static final int MAX_SECTION_HEADING = 120;
    private static final int MAX_SECTION_CHARS = 20000;
    private static final int MAX_SECTIONS = 64;

    public PolicyDocument {
        if (documentId == null || !DOCUMENT_ID.matcher(documentId).matches()) {
            throw new DomainValidationException(
                    "documentId must be 1-48 chars of [a-z0-9._-] starting with a letter or digit");
        }
        require(title, "title", MAX_TITLE);
        if (version == null || !VERSION.matcher(version).matches()) {
            throw new DomainValidationException("version must be 1-31 chars of [A-Za-z0-9._-]");
        }
        if (documentId.length() + version.length() > 58) {
            throw new DomainValidationException(
                    "documentId + version must be at most 58 characters so derived chunk ids stay within 64");
        }
        if (effectiveDate == null) {
            throw new DomainValidationException("effectiveDate is required");
        }
        require(owner, "owner", MAX_OWNER);
        if (classification == null) {
            throw new DomainValidationException("classification is required");
        }
        if (status == null) {
            throw new DomainValidationException("status is required");
        }
        if (sections == null || sections.isEmpty() || sections.size() > MAX_SECTIONS) {
            throw new DomainValidationException("sections must contain 1-" + MAX_SECTIONS + " entries");
        }
        sections = List.copyOf(sections);
    }

    /** Stable provenance hash of the document identity plus full section content. */
    public String contentHash() {
        StringBuilder sb = new StringBuilder(documentId).append('\n').append(version);
        for (Section s : sections) {
            sb.append('\n').append(s.heading()).append('\n').append(s.text());
        }
        return KnowledgeHashes.sha256(sb.toString());
    }

    private static void require(String value, String field, int max) {
        if (value == null || value.isBlank()) {
            throw new DomainValidationException(field + " is required");
        }
        if (value.length() > max) {
            throw new DomainValidationException(field + " must be at most " + max + " characters");
        }
    }
}
