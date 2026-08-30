package com.example.disclosurereview.persistence.repository;

import com.example.disclosurereview.model.ReviewIssueStatus;
import com.example.disclosurereview.persistence.entity.ReviewIssueEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;

public interface ReviewIssueJpaRepository extends JpaRepository<ReviewIssueEntity, Long> {

    List<ReviewIssueEntity> findByTaskIdOrderById(Long taskId);

    Optional<ReviewIssueEntity> findByIdAndTaskId(Long id, Long taskId);

    long countByTaskIdAndIssueStatus(Long taskId, ReviewIssueStatus issueStatus);

    @Transactional
    void deleteByTaskId(Long taskId);

    List<ReviewIssueEntity> findByRuleVersionIdAndIssueStatusOrderByCreatedAtDesc(
            Long ruleVersionId, ReviewIssueStatus issueStatus, Pageable pageable);

    long countByRuleVersionIdAndIssueStatusAndCreatedAtAfter(
            Long ruleVersionId, ReviewIssueStatus issueStatus, java.time.Instant createdAfter);
}
