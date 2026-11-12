package com.capstone.fraudcase.persistence.repository;

import com.capstone.fraudcase.persistence.entity.ReviewDispositionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReviewDispositionRepository extends JpaRepository<ReviewDispositionEntity, String> {

    List<ReviewDispositionEntity> findByCaseIdOrderByReviewedAtAsc(String caseId);
}
