package com.example.disclosurereview.persistence.repository;

import com.example.disclosurereview.persistence.entity.ManualReviewEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ManualReviewJpaRepository extends JpaRepository<ManualReviewEntity, Long> {

    List<ManualReviewEntity> findByTaskIdOrderByReviewedAtDesc(Long taskId);
}
