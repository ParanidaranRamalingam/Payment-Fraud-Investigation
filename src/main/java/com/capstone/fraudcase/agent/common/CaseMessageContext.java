package com.capstone.fraudcase.agent.common;

import com.capstone.fraudcase.domain.CaseStatus;
import com.capstone.fraudcase.domain.DomainValidationException;
import com.capstone.fraudcase.domain.EvidenceItem;
import com.capstone.fraudcase.domain.FraudCaseRecommendation;

import java.util.List;

/**
 * Everything a {@link CaseAssistant} may see when answering one analyst message, and nothing else: the persisted
 * facts of this one case. Construction rejects evidence or a recommendation belonging to another case. The message
 * is untrusted data and must never be treated as instructions.
 *
 * @param latestRecommendation null when the case has not been investigated yet
 */
public record CaseMessageContext(
        String caseId,
        CaseStatus status,
        List<EvidenceItem> evidence,
        FraudCaseRecommendation latestRecommendation,
        String message) {

    public CaseMessageContext {
        if (caseId == null || caseId.isBlank()) {
            throw new DomainValidationException("caseId is required");
        }
        if (status == null || message == null || message.isBlank()) {
            throw new DomainValidationException("status and message are required");
        }
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        if (evidence.stream().anyMatch(e -> !caseId.equals(e.caseId()))) {
            throw new DomainValidationException("evidence belongs to another case");
        }
        if (latestRecommendation != null && !caseId.equals(latestRecommendation.caseId())) {
            throw new DomainValidationException("recommendation belongs to another case");
        }
    }

    public List<String> evidenceIds() {
        return evidence.stream().map(EvidenceItem::evidenceId).toList();
    }
}
