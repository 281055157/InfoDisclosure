package com.example.disclosurereview.persistence.repository;

import com.example.disclosurereview.persistence.entity.ReviewTaskContextEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReviewTaskContextJpaRepository extends JpaRepository<ReviewTaskContextEntity, Long> {

    Optional<ReviewTaskContextEntity> findByTask_Id(Long taskId);
}
