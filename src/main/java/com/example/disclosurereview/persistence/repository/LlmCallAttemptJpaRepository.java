package com.example.disclosurereview.persistence.repository;

import com.example.disclosurereview.persistence.entity.LlmCallAttemptEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LlmCallAttemptJpaRepository extends JpaRepository<LlmCallAttemptEntity, Long> {

    List<LlmCallAttemptEntity> findByTask_IdOrderById(Long taskId);

    List<LlmCallAttemptEntity> findByGovernanceRunIdAndGovernanceGroupIdOrderById(Long runId, Long groupId);

    List<LlmCallAttemptEntity> findByGovernanceRunIdOrderById(Long runId);
}
