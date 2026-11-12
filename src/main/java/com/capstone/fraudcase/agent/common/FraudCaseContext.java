package com.capstone.fraudcase.agent.common;

import com.capstone.fraudcase.domain.DomainValidationException;
import com.capstone.fraudcase.domain.EvidenceItem;
import com.capstone.fraudcase.domain.EvidenceTool;
import com.capstone.fraudcase.domain.PolicyCitation;
import com.capstone.fraudcase.domain.TransactionClaim;
import com.capstone.fraudcase.tools.model.EvidencePayload;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Everything a {@link FraudCaseProcessor} may see for one case, and nothing else. Construction rejects evidence
 * that belongs to another case, so facts cannot leak between cases. The customer statement is untrusted data:
 * processors must never treat it as instructions.
 *
 * @param claim null when the case has no transaction claim on file
 */
public record FraudCaseContext(
        String caseId,
        TransactionClaim claim,
        String customerStatement,
        List<CollectedEvidence> evidence,
        List<PolicyCitation> policyCitations) {

    public FraudCaseContext {
        if (caseId == null || caseId.isBlank()) {
            throw new DomainValidationException("caseId is required");
        }
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        policyCitations = policyCitations == null ? List.of() : List.copyOf(policyCitations);
        for (CollectedEvidence e : evidence) {
            if (!caseId.equals(e.item().caseId())) {
                throw new DomainValidationException("evidence " + e.item().evidenceId() + " belongs to another case");
            }
        }
    }

    public List<String> evidenceIds() {
        return evidence.stream().map(e -> e.item().evidenceId()).toList();
    }

    public Set<String> toolTraceIds() {
        Set<String> ids = new LinkedHashSet<>();
        evidence.stream().map(e -> e.item().toolTraceId()).filter(id -> id != null).forEach(ids::add);
        return ids;
    }

    public Optional<EvidencePayload> payload(EvidenceTool tool) {
        return evidence.stream()
                .filter(e -> e.item().tool() == tool && e.payload() != null)
                .map(CollectedEvidence::payload)
                .findFirst();
    }

    public <T extends EvidencePayload> Optional<T> payload(EvidenceTool tool, Class<T> type) {
        return payload(tool).filter(type::isInstance).map(type::cast);
    }

    /** Evidence items that were requested but returned no data (not found, timeout, unavailable). */
    public List<EvidenceItem> unavailableEvidence() {
        return evidence.stream().map(CollectedEvidence::item).filter(item -> !item.isAvailable()).toList();
    }

    /** One line per unavailable evidence item, suitable for a recommendation's missing information. */
    public List<String> evidenceGaps() {
        return unavailableEvidence().stream()
                .map(item -> (item.tool() == null ? item.provenance().name() : item.tool().toolId())
                        + " returned no evidence (" + item.status() + ")")
                .toList();
    }

    public boolean hasEvidence(EvidenceTool tool) {
        return evidence.stream().anyMatch(e -> e.item().tool() == tool);
    }
}
