package com.example.disclosurereview.governance.service;

import com.example.disclosurereview.governance.domain.*;
import com.example.disclosurereview.governance.persistence.entity.*;
import com.example.disclosurereview.governance.persistence.repository.RuleGovernanceMemoryJpaRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class GovernanceMemoryService {
    private final RuleGovernanceMemoryJpaRepository repository;

    public GovernanceMemoryService(RuleGovernanceMemoryJpaRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<RuleGovernanceMemoryEntity> search(String ruleCode,
                                                   String documentCategory,
                                                   String declaredFileType,
                                                   RootCauseType rootCause,
                                                   int limit) {
        return repository.search(emptyToNull(ruleCode), emptyToNull(documentCategory),
                emptyToNull(declaredFileType), rootCause == null ? null : rootCause.name(),
                PageRequest.of(0, Math.max(1, Math.min(limit, 50))));
    }

    @Transactional
    public RuleGovernanceMemoryEntity recordCase(RuleChangeProposalEntity proposal) {
        RuleGovernanceMemoryEntity memory = base(proposal, GovernanceMemoryType.CASE, GovernanceDecision.UNKNOWN);
        memory.setCaseSummary(proposal.getProblemSummary());
        memory.setAgentSuggestionSummary(proposal.getRootCauseAnalysis());
        memory.setBeforeRuleSnapshotJson(proposal.getBeforeRuleSnapshotJson());
        memory.setFinalRuleSnapshotJson(proposal.getAfterRuleSnapshotJson());
        memory.setBacktestSummaryJson(proposal.getBacktestResultJson());
        memory.setSourceType("AGENT_PROPOSAL");
        memory.setSourceId(proposal.getId());
        return repository.save(memory);
    }

    @Transactional
    public RuleGovernanceMemoryEntity recordDecision(RuleChangeProposalEntity proposal,
                                                     GovernanceDecision decision,
                                                     String reason,
                                                     String humanComment,
                                                     String finalSnapshot) {
        RuleFeedbackGovernanceGroupEntity group = proposal.getGovernanceGroup();
        RuleGovernanceMemoryEntity memory = base(proposal, GovernanceMemoryType.DECISION, decision);
        memory.setDecisionReason(reason);
        memory.setHumanComment(humanComment);
        memory.setCaseSummary(proposal.getProblemSummary());
        memory.setAgentSuggestionSummary(proposal.getRootCauseAnalysis());
        memory.setFinalChangeSummary(proposal.getChangeReason());
        memory.setBeforeRuleSnapshotJson(proposal.getBeforeRuleSnapshotJson());
        memory.setFinalRuleSnapshotJson(finalSnapshot);
        memory.setBacktestSummaryJson(proposal.getBacktestResultJson());
        memory.setSourceType("PROPOSAL_REVIEW");
        memory.setSourceId(proposal.getId());
        return repository.save(memory);
    }

    @Transactional
    public RuleGovernanceMemoryEntity recordEffect(RuleChangeProposalEntity proposal,
                                                   GovernanceDecision decision,
                                                   String summaryJson) {
        RuleGovernanceMemoryEntity memory = base(proposal, GovernanceMemoryType.EFFECT, decision);
        memory.setCaseSummary(proposal.getProblemSummary());
        memory.setEffectSummaryJson(summaryJson);
        memory.setSourceType("EFFECT_EVALUATION");
        memory.setSourceId(proposal.getId());
        return repository.save(memory);
    }

    private RuleGovernanceMemoryEntity base(RuleChangeProposalEntity proposal,
                                            GovernanceMemoryType type,
                                            GovernanceDecision decision) {
        RuleFeedbackGovernanceGroupEntity group = proposal.getGovernanceGroup();
        Instant now = Instant.now();
        RuleGovernanceMemoryEntity memory = new RuleGovernanceMemoryEntity();
        memory.setMemoryType(type);
        memory.setRuleCode(proposal.getRuleCode());
        memory.setRuleVersion(proposal.getSourceRuleVersion());
        memory.setDocumentCategory(group.getDocumentCategory());
        memory.setDeclaredFileType(group.getDeclaredFileType());
        memory.setRootCauseType(proposal.getRootCauseType());
        memory.setProposalType(proposal.getProposalType());
        memory.setProposal(proposal);
        memory.setGovernanceGroup(group);
        memory.setDecision(decision);
        memory.setEnabled(true);
        memory.setCreatedAt(now);
        memory.setUpdatedAt(now);
        return memory;
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
