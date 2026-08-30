package com.example.disclosurereview.governance.agent;

import com.example.disclosurereview.governance.domain.*;
import com.example.disclosurereview.governance.persistence.entity.RuleFeedbackGovernanceGroupEntity;
import com.example.disclosurereview.governance.persistence.repository.RuleFeedbackGovernanceGroupJpaRepository;
import com.example.disclosurereview.governance.service.FeedbackGovernanceGroupService;
import com.example.disclosurereview.persistence.repository.ReviewRuleFeedbackJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;

@Service
public class GovernanceAgentStateService {
    private final RuleFeedbackGovernanceGroupJpaRepository groupRepository;
    private final FeedbackGovernanceGroupService groupService;
    private final ReviewRuleFeedbackJpaRepository feedbackRepository;

    public GovernanceAgentStateService(RuleFeedbackGovernanceGroupJpaRepository groupRepository,
                                       FeedbackGovernanceGroupService groupService,
                                       ReviewRuleFeedbackJpaRepository feedbackRepository) {
        this.groupRepository = groupRepository;
        this.groupService = groupService;
        this.feedbackRepository = feedbackRepository;
    }

    @Transactional
    public RuleFeedbackGovernanceGroupEntity begin(Long runId, Long groupId) {
        RuleFeedbackGovernanceGroupEntity group = groupRepository.findLockedById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("治理分组不存在: " + groupId));
        if (!group.getGovernanceRun().getId().equals(runId)) throw new IllegalArgumentException("治理运行与分组不匹配");
        if (!Set.of(GovernanceGroupStatus.PENDING, GovernanceGroupStatus.FAILED, GovernanceGroupStatus.DEFERRED)
                .contains(group.getStatus())) {
            throw new IllegalStateException("治理分组当前不可分析: " + group.getStatus());
        }
        group.setStatus(GovernanceGroupStatus.ANALYZING);
        group.setErrorMessage(null);
        group.setUpdatedAt(Instant.now());
        groupRepository.save(group);
        groupService.feedbacks(groupId).forEach(feedback -> {
            feedback.setProcessStatus(FeedbackGovernanceStatus.ANALYZING.name());
            feedbackRepository.save(feedback);
        });
        return group;
    }

    @Transactional
    public void fail(Long groupId, String error) {
        groupRepository.findLockedById(groupId).ifPresent(group -> {
            if (group.getStatus() == GovernanceGroupStatus.PROPOSAL_CREATED) return;
            group.setStatus(GovernanceGroupStatus.FAILED);
            group.setErrorMessage(error == null || error.length() <= 2000 ? error : error.substring(0, 2000));
            group.setUpdatedAt(Instant.now());
            groupRepository.save(group);
            groupService.feedbacks(groupId).forEach(feedback -> {
                feedback.setProcessStatus(FeedbackGovernanceStatus.FAILED.name());
                feedbackRepository.save(feedback);
            });
        });
    }

    @Transactional
    public void defer(Long groupId, String reason) {
        groupRepository.findLockedById(groupId).ifPresent(group -> {
            if (group.getStatus() == GovernanceGroupStatus.PROPOSAL_CREATED) return;
            group.setStatus(GovernanceGroupStatus.DEFERRED);
            group.setErrorMessage(reason == null || reason.length() <= 2000 ? reason : reason.substring(0, 2000));
            group.setUpdatedAt(Instant.now());
            groupRepository.save(group);
            groupService.feedbacks(groupId).forEach(feedback -> {
                feedback.setProcessStatus(FeedbackGovernanceStatus.DEFERRED.name());
                feedback.setProcessedAt(Instant.now());
                feedbackRepository.save(feedback);
            });
        });
    }
}
