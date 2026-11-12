package com.capstone.fraudcase.persistence.repository;

import com.capstone.fraudcase.persistence.entity.CaseMessageEntity;
import org.springframework.data.repository.Repository;

import java.util.List;

/** Insert/read only: conversation memory has no update or delete path. Every query is case-scoped. */
public interface CaseMessageRepository extends Repository<CaseMessageEntity, String> {

    CaseMessageEntity save(CaseMessageEntity message);

    List<CaseMessageEntity> findByCaseIdOrderByCreatedAtAsc(String caseId);

    long countByCaseId(String caseId);
}
