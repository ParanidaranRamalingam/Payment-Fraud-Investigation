package com.capstone.fraudcase.persistence.repository;

import com.capstone.fraudcase.persistence.entity.PolicyChunkEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PolicyChunkRepository extends JpaRepository<PolicyChunkEntity, String> {

    void deleteByDocumentRowId(String documentRowId);
}
