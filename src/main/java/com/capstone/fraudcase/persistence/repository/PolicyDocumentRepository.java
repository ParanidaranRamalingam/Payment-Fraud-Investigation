package com.capstone.fraudcase.persistence.repository;

import com.capstone.fraudcase.persistence.entity.PolicyDocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PolicyDocumentRepository extends JpaRepository<PolicyDocumentEntity, String> {

    Optional<PolicyDocumentEntity> findByDocumentIdAndVersion(String documentId, String version);
}
