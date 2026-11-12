package com.capstone.fraudcase.persistence.repository;

import com.capstone.fraudcase.persistence.entity.AuditEventEntity;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Append-only by construction: extends the bare {@link Repository} marker so no save-all/delete/update
 * methods are inherited. Only insert and read operations are declared.
 */
public interface AuditEventRepository extends Repository<AuditEventEntity, String> {

    AuditEventEntity save(AuditEventEntity event);

    Optional<AuditEventEntity> findById(String id);

    List<AuditEventEntity> findByCaseIdOrderByOccurredAtAsc(String caseId);

    long count();
}
