package com.capstone.fraudcase.persistence.audit;

import com.capstone.fraudcase.domain.ActorType;
import com.capstone.fraudcase.domain.AuditEventType;
import com.capstone.fraudcase.domain.CaseStatus;
import com.capstone.fraudcase.domain.SensitiveDataGuard;
import com.capstone.fraudcase.persistence.entity.AuditEventEntity;
import com.capstone.fraudcase.persistence.repository.AuditEventRepository;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The single application path for writing audit events. Append-only: it only inserts and reads (the repository
 * exposes no update/delete and the entity rejects mutation). Details are redacted and bounded before storage.
 */
@Component
public class AuditTrail {

    public static final String SYSTEM_ACTOR = "system";
    static final int MAX_ACTOR_LENGTH = 64;

    private final AuditEventRepository repository;

    public AuditTrail(AuditEventRepository repository) {
        this.repository = repository;
    }

    public AuditEventEntity record(String caseId, AuditEventType type, ActorType actorType, String actorId,
                                   CaseStatus fromStatus, CaseStatus toStatus, String details, String correlationId) {
        return repository.save(new AuditEventEntity(caseId, type, actorType, bounded(actorId, MAX_ACTOR_LENGTH),
                fromStatus, toStatus, sanitize(details), bounded(correlationId, MAX_ACTOR_LENGTH)));
    }

    public AuditEventEntity system(String caseId, AuditEventType type, CaseStatus fromStatus, CaseStatus toStatus,
                                   String details, String correlationId) {
        return record(caseId, type, ActorType.SYSTEM, SYSTEM_ACTOR, fromStatus, toStatus, details, correlationId);
    }

    public List<AuditEventEntity> history(String caseId) {
        return repository.findByCaseIdOrderByOccurredAtAsc(caseId);
    }

    static String sanitize(String details) {
        return bounded(SensitiveDataGuard.redact(details), AuditEventEntity.MAX_DETAILS_LENGTH);
    }

    private static String bounded(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max - 1) + "…";
    }
}
