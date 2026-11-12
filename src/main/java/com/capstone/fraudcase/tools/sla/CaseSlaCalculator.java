package com.capstone.fraudcase.tools.sla;

import com.capstone.fraudcase.domain.EvidenceTool;
import com.capstone.fraudcase.domain.TransactionClaim;
import com.capstone.fraudcase.tools.EvidenceToolProvider;
import com.capstone.fraudcase.tools.ToolException;
import com.capstone.fraudcase.tools.ToolFailure;
import com.capstone.fraudcase.tools.ToolRequest;
import com.capstone.fraudcase.tools.model.CaseSla;
import com.capstone.fraudcase.tools.model.CaseSla.Priority;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

/**
 * {@code calculateCaseSla}: deterministic review priority and due time from the claim type, amount and case
 * creation time, evaluated at the request's {@code asOf} instant.
 *
 * <pre>
 * UNRECOGNISED_TRANSACTION, amount &gt;= 1000 -&gt; P1, 24 h
 * UNRECOGNISED_TRANSACTION                  -&gt; P2, 48 h
 * DUPLICATE_CHARGE                          -&gt; P2, 72 h
 * MERCHANT_ISSUE, OTHER                     -&gt; P3, 120 h
 * </pre>
 */
@Component
public class CaseSlaCalculator implements EvidenceToolProvider {

    public static final String SOURCE = "sla-calculator:v1";
    static final BigDecimal HIGH_VALUE_THRESHOLD = new BigDecimal("1000");

    @Override
    public EvidenceTool tool() {
        return EvidenceTool.CASE_SLA;
    }

    @Override
    public String source() {
        return SOURCE;
    }

    @Override
    public CaseSla fetch(ToolRequest request) {
        if (request.caseCreatedAt() == null) {
            throw new ToolException(ToolFailure.UNAVAILABLE, "case creation time is unknown");
        }
        TransactionClaim claim = request.claim();
        Priority priority = switch (claim.claimType()) {
            case UNRECOGNISED_TRANSACTION -> claim.amount().compareTo(HIGH_VALUE_THRESHOLD) >= 0 ? Priority.P1 : Priority.P2;
            case DUPLICATE_CHARGE -> Priority.P2;
            case MERCHANT_ISSUE, OTHER -> Priority.P3;
        };
        int slaHours = switch (claim.claimType()) {
            case UNRECOGNISED_TRANSACTION -> priority == Priority.P1 ? 24 : 48;
            case DUPLICATE_CHARGE -> 72;
            case MERCHANT_ISSUE, OTHER -> 120;
        };
        Instant dueAt = request.caseCreatedAt().plus(Duration.ofHours(slaHours));
        Instant asOf = request.asOf();
        return new CaseSla(priority, slaHours, request.caseCreatedAt(), dueAt, asOf,
                Duration.between(asOf, dueAt).toHours(), asOf.isAfter(dueAt));
    }
}
