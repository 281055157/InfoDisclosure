package com.example.disclosurereview.persistence.repository;

import com.example.disclosurereview.persistence.entity.ReviewRuleDefinitionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReviewRuleDefinitionJpaRepository extends JpaRepository<ReviewRuleDefinitionEntity, Long> {

    List<ReviewRuleDefinitionEntity> findByEnabledTrueOrderByRuleCodeAsc();

    List<ReviewRuleDefinitionEntity> findByEnabledTrueOrderByPriorityDescRuleCodeAsc();

    Optional<ReviewRuleDefinitionEntity> findByRuleCode(String ruleCode);
}
