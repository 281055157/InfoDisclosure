package com.example.disclosurereview.governance.service;

import com.example.disclosurereview.governance.domain.FeedbackGovernanceStatus;
import com.example.disclosurereview.governance.domain.GovernanceGroupStatus;
import com.example.disclosurereview.governance.domain.GovernanceRunStatus;
import com.example.disclosurereview.governance.persistence.entity.RuleFeedbackGovernanceGroupEntity;
import com.example.disclosurereview.governance.persistence.entity.RuleFeedbackGovernanceGroupItemEntity;
import com.example.disclosurereview.governance.persistence.entity.RuleGovernanceRunEntity;
import com.example.disclosurereview.governance.persistence.repository.RuleChangeProposalJpaRepository;
import com.example.disclosurereview.governance.persistence.repository.RuleFeedbackGovernanceGroupItemJpaRepository;
import com.example.disclosurereview.governance.persistence.repository.RuleFeedbackGovernanceGroupJpaRepository;
import com.example.disclosurereview.governance.persistence.repository.RuleGovernanceRunJpaRepository;
import com.example.disclosurereview.persistence.entity.ReviewRuleFeedbackEntity;
import com.example.disclosurereview.persistence.repository.ReviewRuleFeedbackJpaRepository;
import com.example.disclosurereview.service.AuditLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class GovernanceRecordDeletionService {
    private static final Set<GovernanceGroupStatus> DELETABLE_GROUP_STATUSES = Set.of(
            GovernanceGroupStatus.FAILED, GovernanceGroupStatus.DEFERRED);

    private final RuleGovernanceRunJpaRepository runRepository;
    private final RuleFeedbackGovernanceGroupJpaRepository groupRepository;
    private final RuleFeedbackGovernanceGroupItemJpaRepository itemRepository;
    private final RuleChangeProposalJpaRepository proposalRepository;
    private final ReviewRuleFeedbackJpaRepository feedbackRepository;
    private final AuditLogService auditLogService;

    public GovernanceRecordDeletionService(RuleGovernanceRunJpaRepository runRepository,
                                           RuleFeedbackGovernanceGroupJpaRepository groupRepository,
                                           RuleFeedbackGovernanceGroupItemJpaRepository itemRepository,
                                           RuleChangeProposalJpaRepository proposalRepository,
                                           ReviewRuleFeedbackJpaRepository feedbackRepository,
                                           AuditLogService auditLogService) {
        this.runRepository = runRepository;
        this.groupRepository = groupRepository;
        this.itemRepository = itemRepository;
        this.proposalRepository = proposalRepository;
        this.feedbackRepository = feedbackRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public DeletionResult deleteGroup(Long groupId, String operator) {
        RuleFeedbackGovernanceGroupEntity group = groupRepository.findLockedById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("治理分组不存在: " + groupId));
        requireDeletable(group);
        RuleGovernanceRunEntity run = group.getGovernanceRun();
        List<ReviewRuleFeedbackEntity> released = releaseFeedbacks(group);
        auditLogService.recordGovernance(run.getId(), null, null, group.getRuleCode(), feedbackIds(released),
                "GOVERNANCE_GROUP_DELETED", operator,
                "删除治理分组并将来源反馈恢复为 PENDING", group.getStatus().name(), "DELETED");
        groupRepository.delete(group);
        groupRepository.flush();
        run.setCreatedGroupCount(Math.max(0, run.getCreatedGroupCount() - 1));
        if (group.getStatus() == GovernanceGroupStatus.FAILED) {
            run.setFailedGroupCount(Math.max(0, run.getFailedGroupCount() - 1));
        }
        run.setUpdatedAt(Instant.now());
        runRepository.save(run);
        return new DeletionResult(null, groupId, released.size());
    }

    @Transactional
    public DeletionResult deleteRun(Long runId, String operator) {
        RuleGovernanceRunEntity run = runRepository.findLockedById(runId)
                .orElseThrow(() -> new IllegalArgumentException("治理运行不存在: " + runId));
        if (run.getStatus() == GovernanceRunStatus.RUNNING || run.getStatus() == GovernanceRunStatus.CREATED) {
            throw new IllegalStateException("运行中的治理记录不能删除，请等待完成后重试");
        }
        List<RuleFeedbackGovernanceGroupEntity> groups = groupRepository.findByGovernanceRun_IdOrderById(runId);
        groups.forEach(this::requireDeletable);
        Set<ReviewRuleFeedbackEntity> released = new LinkedHashSet<>();
        groups.forEach(group -> released.addAll(releaseFeedbacks(group)));
        auditLogService.recordGovernance(runId, null, null, null, feedbackIds(List.copyOf(released)),
                "GOVERNANCE_RUN_DELETED", operator,
                "删除治理运行及其分组，并将来源反馈恢复为 PENDING", run.getStatus().name(), "DELETED");
        runRepository.delete(run);
        runRepository.flush();
        return new DeletionResult(runId, null, released.size());
    }

    private void requireDeletable(RuleFeedbackGovernanceGroupEntity group) {
        if (!DELETABLE_GROUP_STATUSES.contains(group.getStatus())) {
            throw new IllegalStateException("仅 FAILED/DEFERRED 且未形成提案的分组允许删除，当前状态: " + group.getStatus());
        }
        if (proposalRepository.existsByGovernanceGroup_Id(group.getId())) {
            throw new IllegalStateException("分组已形成治理提案，为保护审批和规则变更审计记录，不能删除");
        }
    }

    private List<ReviewRuleFeedbackEntity> releaseFeedbacks(RuleFeedbackGovernanceGroupEntity group) {
        List<ReviewRuleFeedbackEntity> feedbacks = itemRepository
                .findByGroup_IdOrderByFeedback_CreatedAtDesc(group.getId()).stream()
                .map(RuleFeedbackGovernanceGroupItemEntity::getFeedback)
                .toList();
        feedbacks.forEach(feedback -> {
            feedback.setProcessStatus(FeedbackGovernanceStatus.PENDING.name());
            feedback.setProcessedAt(null);
        });
        return feedbackRepository.saveAll(feedbacks);
    }

    private String feedbackIds(List<ReviewRuleFeedbackEntity> feedbacks) {
        return feedbacks.stream().map(ReviewRuleFeedbackEntity::getId).map(String::valueOf)
                .collect(Collectors.joining(","));
    }

    public record DeletionResult(Long deletedRunId, Long deletedGroupId, int releasedFeedbackCount) {}
}
