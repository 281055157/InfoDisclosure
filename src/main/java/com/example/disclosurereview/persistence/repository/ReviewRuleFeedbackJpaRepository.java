package com.example.disclosurereview.persistence.repository;

import com.example.disclosurereview.persistence.entity.ReviewRuleFeedbackEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.time.Instant;
import java.util.Collection;

public interface ReviewRuleFeedbackJpaRepository extends JpaRepository<ReviewRuleFeedbackEntity, Long> {

    List<ReviewRuleFeedbackEntity> findByTask_IdOrderByCreatedAtDesc(Long taskId);

    Optional<ReviewRuleFeedbackEntity> findFirstByIssue_IdAndFeedbackTypeOrderByCreatedAtDesc(Long issueId,
                                                                                              String feedbackType);

    List<ReviewRuleFeedbackEntity> findAllByOrderByCreatedAtDesc();

    List<ReviewRuleFeedbackEntity> findByProcessStatusInAndCreatedAtAfterOrderByCreatedAtAsc(
            Collection<String> statuses, Instant createdAfter);
}
