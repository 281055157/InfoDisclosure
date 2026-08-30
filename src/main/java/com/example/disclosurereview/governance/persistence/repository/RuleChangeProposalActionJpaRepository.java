package com.example.disclosurereview.governance.persistence.repository;

import com.example.disclosurereview.governance.persistence.entity.RuleChangeProposalActionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RuleChangeProposalActionJpaRepository extends JpaRepository<RuleChangeProposalActionEntity, Long> {
    List<RuleChangeProposalActionEntity> findByProposal_IdOrderBySequenceNoAsc(Long proposalId);
    long countByProposal_Id(Long proposalId);
    Optional<RuleChangeProposalActionEntity> findByDraftRuleVersion_Id(Long ruleVersionId);
}
