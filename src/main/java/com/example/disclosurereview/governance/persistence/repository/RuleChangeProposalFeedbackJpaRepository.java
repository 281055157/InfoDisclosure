package com.example.disclosurereview.governance.persistence.repository;

import com.example.disclosurereview.governance.persistence.entity.RuleChangeProposalFeedbackEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RuleChangeProposalFeedbackJpaRepository extends JpaRepository<RuleChangeProposalFeedbackEntity, Long> {
    List<RuleChangeProposalFeedbackEntity> findByProposal_IdOrderByFeedback_CreatedAtDesc(Long proposalId);
    long countByProposal_Id(Long proposalId);
}
