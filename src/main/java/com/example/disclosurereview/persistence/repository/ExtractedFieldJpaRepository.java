package com.example.disclosurereview.persistence.repository;

import com.example.disclosurereview.persistence.entity.ExtractedFieldEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface ExtractedFieldJpaRepository extends JpaRepository<ExtractedFieldEntity, Long> {

    List<ExtractedFieldEntity> findByTaskIdOrderById(Long taskId);

    @Transactional
    void deleteByTaskId(Long taskId);
}
