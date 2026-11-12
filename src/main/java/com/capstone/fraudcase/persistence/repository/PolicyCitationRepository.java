package com.capstone.fraudcase.persistence.repository;

import com.capstone.fraudcase.persistence.entity.PolicyCitationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PolicyCitationRepository extends JpaRepository<PolicyCitationEntity, String> {

    List<PolicyCitationEntity> findByRecommendationIdOrderByRelevanceScoreDesc(String recommendationId);

    List<PolicyCitationEntity> findByCaseIdOrderByCreatedAtAsc(String caseId);
}
