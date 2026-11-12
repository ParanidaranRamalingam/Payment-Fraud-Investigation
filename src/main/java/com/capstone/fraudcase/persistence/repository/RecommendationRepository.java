package com.capstone.fraudcase.persistence.repository;

import com.capstone.fraudcase.persistence.entity.RecommendationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RecommendationRepository extends JpaRepository<RecommendationEntity, String> {

    Optional<RecommendationEntity> findFirstByCaseIdOrderByCreatedAtDesc(String caseId);

    List<RecommendationEntity> findByCaseIdOrderByCreatedAtDesc(String caseId);

    Optional<RecommendationEntity> findByIdAndCaseId(String id, String caseId);
}
