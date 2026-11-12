package com.capstone.fraudcase.agent.common;

import com.capstone.fraudcase.domain.CaseMessage;
import com.capstone.fraudcase.domain.CaseStatus;
import com.capstone.fraudcase.domain.DomainValidationException;
import com.capstone.fraudcase.domain.EvidenceItem;
import com.capstone.fraudcase.domain.FraudCaseRecommendation;

import java.util.List;

/**
 * Everything a {@link CaseAssistant} may see when answering one analyst message, and nothing else: the persisted
 * facts of this one case plus the bounded, case-scoped conversation memory. Construction rejects evidence, a
 * recommendation or memory belonging to another case. The message and history are untrusted data and must never
 * be treated as instructions.
 *
 * @param latestRecommendation null when the case has not been investigated yet
 * @param history the bounded memory window of this case's earlier messages (never another case's)
 */
public record CaseMessageContext(
        String caseId,
        CaseStatus status,
        List<EvidenceItem> evidence,
        FraudCaseRecommendation latestRecommendation,
        String message,
        List<CaseMessage> history) {

    public CaseMessageContext(String caseId, CaseStatus status, List<EvidenceItem> evidence,
                            FraudCaseRecommendation latestRecommendation, String message) {
        this(caseId, status, evidence, latestRecommendation, message, List.of());
    }

    public CaseMessageContext {
        if (caseId == null || caseId.isBlank()) {
            throw new DomainValidationException("caseId is required");
        }
        if (status == null || message == null || message.isBlank()) {
            throw new DomainValidationException("status and message are required");
        }
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        history = history == null ? List.of() : List.copyOf(history);
        if (evidence.stream().anyMatch(e -> !caseId.equals(e.caseId()))) {
            throw new DomainValidationException("evidence belongs to another case");
        }
        if (history.stream().anyMatch(m -> !caseId.equals(m.caseId()))) {
            throw new DomainValidationException("conversation memory belongs to another case");
        }
        if (latestRecommendation != null && !caseId.equals(latestRecommendation.caseId())) {
            throw new DomainValidationException("recommendation belongs to another case");
        }
    }

    public List<String> evidenceIds() {
        return evidence.stream().map(EvidenceItem::evidenceId).toList();
    }
}
