package com.example.disclosurereview.governance.persistence.repository;

import com.example.disclosurereview.governance.persistence.entity.RuleGovernanceTraceSpanEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RuleGovernanceTraceSpanJpaRepository extends JpaRepository<RuleGovernanceTraceSpanEntity, Long> {
    List<RuleGovernanceTraceSpanEntity> findByGovernanceRunIdOrderByStartedAtAscIdAsc(Long runId);
    Optional<RuleGovernanceTraceSpanEntity> findBySpanId(String spanId);
}
