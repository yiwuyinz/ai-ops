package com.aops.agent.repository;

import com.aops.agent.domain.CaseEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CaseRepository extends JpaRepository<CaseEntity, String> {

    Optional<CaseEntity> findByAlertFingerprint(String fingerprint);

    List<CaseEntity> findByStatusOrderByCreatedAtDesc(com.aops.agent.domain.CaseStatus status);

    List<CaseEntity> findTop50ByOrderByCreatedAtDesc();
}
