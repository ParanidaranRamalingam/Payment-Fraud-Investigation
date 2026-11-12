package com.capstone.fraudcase.persistence.repository;

import com.capstone.fraudcase.persistence.entity.PolicyRetrievalTraceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PolicyRetrievalTraceRepository extends JpaRepository<PolicyRetrievalTraceEntity, String> {

    List<PolicyRetrievalTraceEntity> findByCaseIdOrderByCreatedAtAsc(String caseId);
}
