package com.aops.agent.repository;

import com.aops.agent.domain.EvidenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EvidenceRepository extends JpaRepository<EvidenceEntity, String> {

    List<EvidenceEntity> findByCaseIdOrderByStepAsc(String caseId);
}
