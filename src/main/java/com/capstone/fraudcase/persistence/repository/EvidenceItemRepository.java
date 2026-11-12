package com.capstone.fraudcase.persistence.repository;

import com.capstone.fraudcase.persistence.entity.EvidenceItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface EvidenceItemRepository extends JpaRepository<EvidenceItemEntity, String> {

    List<EvidenceItemEntity> findByCaseIdOrderByCollectedAtAsc(String caseId);

    /** Case-scoped lookup: evidence IDs belonging to another case are never returned. */
    List<EvidenceItemEntity> findByCaseIdAndIdIn(String caseId, Collection<String> ids);
}
