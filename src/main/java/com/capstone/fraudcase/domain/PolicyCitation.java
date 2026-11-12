package com.capstone.fraudcase.domain;

import java.time.LocalDate;

import static com.capstone.fraudcase.domain.DomainChecks.required;

public record PolicyCitation(
        String documentId,
        String documentTitle,
        String documentVersion,
        LocalDate effectiveDate,
        String section,
        String owner,
        String chunkId,
        double relevanceScore,
        String excerpt) {

    public PolicyCitation {
        required(documentId, "documentId");
        required(documentTitle, "documentTitle");
        required(documentVersion, "documentVersion");
        required(effectiveDate, "effectiveDate");
        required(section, "section");
        required(owner, "owner");
        required(chunkId, "chunkId");
        required(excerpt, "excerpt");
        if (relevanceScore < 0.0 || relevanceScore > 1.0 || Double.isNaN(relevanceScore)) {
            throw new DomainValidationException("relevanceScore must be between 0 and 1");
        }
    }
}
