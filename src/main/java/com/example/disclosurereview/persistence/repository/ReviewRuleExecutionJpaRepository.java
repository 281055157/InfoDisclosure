package com.example.disclosurereview.persistence.repository;

import com.example.disclosurereview.persistence.entity.ReviewRuleExecutionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import org.springframework.data.domain.Pageable;

public interface ReviewRuleExecutionJpaRepository extends JpaRepository<ReviewRuleExecutionEntity, Long> {

    List<ReviewRuleExecutionEntity> findByTask_IdOrderByCreatedAtDesc(Long taskId);

    List<ReviewRuleExecutionEntity> findByRuleIdOrderByCreatedAtDesc(Long ruleId);

    List<ReviewRuleExecutionEntity> findByRuleVersionIdAndMatchedOrderByCreatedAtDesc(
            Long ruleVersionId, boolean matched, Pageable pageable);

    long countByRuleVersionIdAndCreatedAtAfter(Long ruleVersionId, java.time.Instant createdAfter);

    long countByRuleVersionIdAndMatchedTrueAndCreatedAtAfter(Long ruleVersionId, java.time.Instant createdAfter);

    List<ReviewRuleExecutionEntity> findByRuleVersionIdAndTask_IdInOrderByCreatedAtDesc(
            Long ruleVersionId, java.util.Collection<Long> taskIds);
}
