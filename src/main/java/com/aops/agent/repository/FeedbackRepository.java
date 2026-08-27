package com.aops.agent.repository;

import com.aops.agent.domain.FeedbackEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FeedbackRepository extends JpaRepository<FeedbackEntity, String> {

    List<FeedbackEntity> findByCaseId(String caseId);
}
