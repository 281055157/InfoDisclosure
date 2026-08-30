package com.example.disclosurereview.governance.service;

import com.example.disclosurereview.config.FeedbackGovernanceProperties;
import com.example.disclosurereview.governance.domain.*;
import com.example.disclosurereview.governance.persistence.entity.RuleChangeProposalEntity;
import com.example.disclosurereview.governance.persistence.repository.RuleChangeProposalJpaRepository;
import com.example.disclosurereview.model.ReviewIssueStatus;
import com.example.disclosurereview.persistence.repository.ReviewIssueJpaRepository;
import com.example.disclosurereview.persistence.repository.ReviewRuleExecutionJpaRepository;
import com.example.disclosurereview.service.AuditLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class GovernanceEffectEvaluationService {
    private final RuleChangeProposalJpaRepository proposalRepository;
    private final ReviewRuleExecutionJpaRepository executionRepository;
    private final ReviewIssueJpaRepository issueRepository;
    private final FeedbackGovernanceProperties properties;
    private final GovernanceMemoryService memoryService;
    private final GovernanceJsonService jsonService;
    private final AuditLogService auditLogService;

    public GovernanceEffectEvaluationService(RuleChangeProposalJpaRepository proposalRepository,
                                             ReviewRuleExecutionJpaRepository executionRepository,
                                             ReviewIssueJpaRepository issueRepository,
                                             FeedbackGovernanceProperties properties,
                                             GovernanceMemoryService memoryService,
                                             GovernanceJsonService jsonService,
                                             AuditLogService auditLogService) {
        this.proposalRepository = proposalRepository; this.executionRepository = executionRepository;
        this.issueRepository = issueRepository; this.properties = properties; this.memoryService = memoryService;
        this.jsonService = jsonService; this.auditLogService = auditLogService;
    }

    @Transactional
    public EffectResult evaluate(Long proposalId, String operator) {
        RuleChangeProposalEntity proposal = proposalRepository.findLockedById(proposalId)
                .orElseThrow(() -> new IllegalArgumentException("提案不存在: " + proposalId));
        if (proposal.getProposalStatus() != ProposalStatus.APPLIED || proposal.getDraftRuleVersion() == null) {
            throw new IllegalStateException("只有已应用并关联规则版本的提案可以评估效果");
        }
        Long newVersionId = proposal.getDraftRuleVersion().getId();
        Long oldVersionId = proposal.getSourceRuleVersionEntity() == null ? null : proposal.getSourceRuleVersionEntity().getId();
        Instant since = Instant.now().minus(properties.getEffectEvaluation().getEvaluationDays(), ChronoUnit.DAYS);
        long newExecutions = executionRepository.countByRuleVersionIdAndCreatedAtAfter(newVersionId, since);
        long newHits = executionRepository.countByRuleVersionIdAndMatchedTrueAndCreatedAtAfter(newVersionId, since);
        long newFalsePositives = issueRepository.countByRuleVersionIdAndIssueStatusAndCreatedAtAfter(
                newVersionId, ReviewIssueStatus.FALSE_POSITIVE, since);
        long oldExecutions = oldVersionId == null ? 0 : executionRepository.countByRuleVersionIdAndCreatedAtAfter(oldVersionId, since);
        long oldFalsePositives = oldVersionId == null ? 0 : issueRepository.countByRuleVersionIdAndIssueStatusAndCreatedAtAfter(
                oldVersionId, ReviewIssueStatus.FALSE_POSITIVE, since);
        GovernanceDecision decision;
        if (newExecutions < properties.getEffectEvaluation().getMinimumExecutionCount()) {
            decision = GovernanceDecision.UNKNOWN;
        } else {
            double oldRate = oldExecutions == 0 ? 0 : (double) oldFalsePositives / oldExecutions;
            double newRate = (double) newFalsePositives / newExecutions;
            decision = newFalsePositives == 0 || (oldExecutions > 0 && newRate < oldRate)
                    ? GovernanceDecision.EFFECTIVE : GovernanceDecision.INEFFECTIVE;
        }
        EffectResult result = new EffectResult(proposalId, newVersionId, newExecutions, newHits,
                newFalsePositives, oldVersionId, oldExecutions, oldFalsePositives, decision);
        memoryService.recordEffect(proposal, decision, jsonService.json(result));
        auditLogService.recordGovernance(proposal.getGovernanceRun().getId(), proposal.getGovernanceGroup().getId(),
                proposal.getId(), proposal.getRuleCode(), null, "GOVERNANCE_EFFECT_EVALUATED", operator,
                "治理效果评估: " + decision, null, jsonService.json(result));
        return result;
    }

    public record EffectResult(Long proposalId, Long newRuleVersionId, long executionCount, long hitCount,
                               long falsePositiveCount, Long sourceRuleVersionId, long sourceExecutionCount,
                               long sourceFalsePositiveCount, GovernanceDecision decision) {}
}
