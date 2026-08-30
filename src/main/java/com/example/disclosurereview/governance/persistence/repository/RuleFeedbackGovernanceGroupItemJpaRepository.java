package com.example.disclosurereview.governance.persistence.repository;

import com.example.disclosurereview.governance.persistence.entity.RuleFeedbackGovernanceGroupItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RuleFeedbackGovernanceGroupItemJpaRepository extends JpaRepository<RuleFeedbackGovernanceGroupItemEntity, Long> {
    List<RuleFeedbackGovernanceGroupItemEntity> findByGroup_IdOrderByFeedback_CreatedAtDesc(Long groupId);
    boolean existsByFeedback_Id(Long feedbackId);
    boolean existsByGroup_IdAndFeedback_Task_Id(Long groupId, Long taskId);
    long countByGroup_Id(Long groupId);
}
