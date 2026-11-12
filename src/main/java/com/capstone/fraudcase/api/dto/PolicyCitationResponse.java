package com.capstone.fraudcase.api.dto;

import com.capstone.fraudcase.domain.PolicyCitation;

import java.time.LocalDate;

public record PolicyCitationResponse(
        String documentId,
        String documentTitle,
        String documentVersion,
        LocalDate effectiveDate,
        String section,
        String owner,
        String chunkId,
        double relevanceScore,
        String excerpt) {

    public static PolicyCitationResponse from(PolicyCitation citation) {
        return new PolicyCitationResponse(citation.documentId(), citation.documentTitle(),
                citation.documentVersion(), citation.effectiveDate(), citation.section(), citation.owner(),
                citation.chunkId(), citation.relevanceScore(), citation.excerpt());
    }
}
