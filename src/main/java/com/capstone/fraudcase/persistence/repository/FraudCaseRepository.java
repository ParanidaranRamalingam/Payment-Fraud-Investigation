package com.capstone.fraudcase.persistence.repository;

import com.capstone.fraudcase.domain.CaseStatus;
import com.capstone.fraudcase.persistence.entity.FraudCaseEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FraudCaseRepository extends JpaRepository<FraudCaseEntity, String> {

    List<FraudCaseEntity> findByStatusOrderByCreatedAtAsc(CaseStatus status);

    List<FraudCaseEntity> findByCreatedByOrderByCreatedAtDesc(String createdBy);

    long countByStatus(CaseStatus status);
}
