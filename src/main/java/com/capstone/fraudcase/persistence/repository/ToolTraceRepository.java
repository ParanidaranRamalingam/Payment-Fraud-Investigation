package com.capstone.fraudcase.persistence.repository;

import com.capstone.fraudcase.persistence.entity.ToolTraceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ToolTraceRepository extends JpaRepository<ToolTraceEntity, String> {

    List<ToolTraceEntity> findByCaseIdOrderByStartedAtAsc(String caseId);
}
