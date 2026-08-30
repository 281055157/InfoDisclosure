package com.example.disclosurereview.governance.persistence.repository;

import com.example.disclosurereview.governance.persistence.entity.RuleGovernanceToolCallEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RuleGovernanceToolCallJpaRepository extends JpaRepository<RuleGovernanceToolCallEntity, Long> {
    List<RuleGovernanceToolCallEntity> findByGovernanceGroup_IdOrderById(Long groupId);
    List<RuleGovernanceToolCallEntity> findByGovernanceRun_IdOrderById(Long runId);
    Optional<RuleGovernanceToolCallEntity> findFirstByGovernanceRun_IdAndGovernanceGroup_IdAndToolNameAndArgumentHashAndCallStatusOrderByIdDesc(
            Long runId, Long groupId, String toolName, String argumentHash, String callStatus);
    Optional<RuleGovernanceToolCallEntity> findFirstByGovernanceRun_IdAndGovernanceGroup_IdAndToolNameAndCandidateHashAndCallStatusOrderByIdDesc(
            Long runId, Long groupId, String toolName, String candidateHash, String callStatus);
}
