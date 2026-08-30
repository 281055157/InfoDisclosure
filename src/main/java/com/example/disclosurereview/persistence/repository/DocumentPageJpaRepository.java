package com.example.disclosurereview.persistence.repository;

import com.example.disclosurereview.persistence.entity.DocumentPageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface DocumentPageJpaRepository extends JpaRepository<DocumentPageEntity, Long> {

    List<DocumentPageEntity> findByTaskIdOrderByPageNumber(Long taskId);

    Optional<DocumentPageEntity> findByTaskIdAndPageNumber(Long taskId, int pageNumber);

    @Transactional
    void deleteByTaskId(Long taskId);
}
