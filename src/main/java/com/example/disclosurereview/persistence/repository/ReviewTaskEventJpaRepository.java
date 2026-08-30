package com.example.disclosurereview.persistence.repository;

import com.example.disclosurereview.model.ReviewStage;
import com.example.disclosurereview.persistence.entity.ReviewTaskEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReviewTaskEventJpaRepository extends JpaRepository<ReviewTaskEventEntity, Long> {

    List<ReviewTaskEventEntity> findByTask_IdOrderByCreatedAtAsc(Long taskId);

    List<ReviewTaskEventEntity> findTop50ByEventStatusOrderByCreatedAtAsc(String eventStatus);

    List<ReviewTaskEventEntity> findByTask_IdAndStageAndEventTypeAndEventStatusInOrderByCreatedAtAsc(
            Long taskId,
            ReviewStage stage,
            String eventType,
            List<String> eventStatuses);

    Optional<ReviewTaskEventEntity> findFirstByTask_IdAndStageAndEventTypeAndEventStatusInOrderByCreatedAtDesc(
            Long taskId,
            ReviewStage stage,
            String eventType,
            List<String> eventStatuses);
}
