package com.capstone.fraudcase.tools.model;

import com.capstone.fraudcase.domain.EvidenceTool;

import java.time.Instant;
import java.util.Map;

import static com.capstone.fraudcase.tools.model.Attributes.range;
import static com.capstone.fraudcase.tools.model.Attributes.require;

/** Result of {@code calculateCaseSla}: review priority and deadline derived from the claim and case age. */
public record CaseSla(
        Priority priority,
        int slaHours,
        Instant caseCreatedAt,
        Instant dueAt,
        Instant asOf,
        long hoursRemaining,
        boolean breached) implements EvidencePayload {

    public enum Priority { P1, P2, P3 }

    public CaseSla {
        require(priority, "priority");
        require(caseCreatedAt, "caseCreatedAt");
        require(dueAt, "dueAt");
        require(asOf, "asOf");
        range(slaHours, 1, 24 * 30, "slaHours");
        if (breached != asOf.isAfter(dueAt)) {
            throw new IllegalArgumentException("breached must reflect asOf vs dueAt");
        }
    }

    @Override
    public EvidenceTool tool() {
        return EvidenceTool.CASE_SLA;
    }

    @Override
    public String summary() {
        return "Priority %s, %d h SLA: due %s; %s."
                .formatted(priority, slaHours, dueAt,
                        breached ? "SLA breached by " + Math.abs(hoursRemaining) + " h" : hoursRemaining + " h remaining");
    }

    @Override
    public Map<String, String> attributes() {
        return Attributes.of("priority", priority, "slaHours", slaHours, "caseCreatedAt", caseCreatedAt,
                "dueAt", dueAt, "asOf", asOf, "hoursRemaining", hoursRemaining, "breached", breached);
    }
}
