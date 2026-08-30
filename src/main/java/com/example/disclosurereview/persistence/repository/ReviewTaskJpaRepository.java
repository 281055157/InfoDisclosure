package com.example.disclosurereview.persistence.repository;

import com.example.disclosurereview.model.BusinessRisk;
import com.example.disclosurereview.model.ReviewTaskStatus;
import com.example.disclosurereview.persistence.entity.ReviewTaskEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.Instant;
import java.util.Optional;

public interface ReviewTaskJpaRepository extends JpaRepository<ReviewTaskEntity, Long>,
        JpaSpecificationExecutor<ReviewTaskEntity> {

    Optional<ReviewTaskEntity> findByIdempotencyKey(String idempotencyKey);

    long countByStatus(ReviewTaskStatus status);

    long countByBusinessRisk(BusinessRisk businessRisk);

    long countByCompletedAtGreaterThanEqual(Instant completedAt);
}
