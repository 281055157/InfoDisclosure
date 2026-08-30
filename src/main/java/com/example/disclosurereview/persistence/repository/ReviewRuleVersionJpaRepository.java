package com.example.disclosurereview.persistence.repository;

import com.example.disclosurereview.persistence.entity.ReviewRuleVersionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReviewRuleVersionJpaRepository extends JpaRepository<ReviewRuleVersionEntity, Long> {

    List<ReviewRuleVersionEntity> findByVersionCode(String versionCode);

    Optional<ReviewRuleVersionEntity> findByIdAndRuleDefinition_Id(Long id, Long ruleDefinitionId);

    List<ReviewRuleVersionEntity> findByRuleDefinition_IdOrderByVersionNumberDesc(Long ruleDefinitionId);

    List<ReviewRuleVersionEntity> findByRuleDefinition_IdAndStatusOrderByVersionNumberDesc(Long ruleDefinitionId,
                                                                                           String status);

    Optional<ReviewRuleVersionEntity> findFirstByRuleDefinition_IdOrderByVersionNumberDesc(Long ruleDefinitionId);
}
