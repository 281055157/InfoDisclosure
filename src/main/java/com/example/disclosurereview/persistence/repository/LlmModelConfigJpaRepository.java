package com.example.disclosurereview.persistence.repository;

import com.example.disclosurereview.persistence.entity.LlmModelConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LlmModelConfigJpaRepository extends JpaRepository<LlmModelConfigEntity, Long> {

    List<LlmModelConfigEntity> findByEnabledTrueAndProvider_EnabledTrueOrderByPriorityDesc();

    Optional<LlmModelConfigEntity> findByModelCode(String modelCode);
}
