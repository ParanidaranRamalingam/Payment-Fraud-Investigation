package com.capstone.fraudcase.agent.common;

import com.capstone.fraudcase.domain.DomainValidationException;
import com.capstone.fraudcase.domain.EvidenceItem;
import com.capstone.fraudcase.domain.ProvenanceType;
import com.capstone.fraudcase.tools.model.EvidencePayload;

import java.util.Objects;

/**
 * A persisted evidence item together with its typed payload. Available tool evidence must carry the payload for
 * its own tool, with a matching content hash; unavailable evidence never carries one.
 */
public record CollectedEvidence(EvidenceItem item, EvidencePayload payload) {

    public CollectedEvidence {
        Objects.requireNonNull(item, "item");
        boolean toolResult = item.provenance() == ProvenanceType.TOOL_RESULT;
        if (!item.isAvailable() && payload != null) {
            throw new DomainValidationException("unavailable evidence must not carry a payload");
        }
        if (toolResult && item.isAvailable()) {
            if (payload == null || payload.tool() != item.tool()) {
                throw new DomainValidationException("available tool evidence must carry the payload of its tool");
            }
            if (item.contentHash() != null && !item.contentHash().equals(payload.contentHash())) {
                throw new DomainValidationException("evidence content hash does not match its payload");
            }
        }
    }
}
