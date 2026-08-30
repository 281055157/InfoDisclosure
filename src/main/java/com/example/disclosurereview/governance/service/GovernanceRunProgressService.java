package com.example.disclosurereview.governance.service;

import com.example.disclosurereview.governance.domain.*;
import com.example.disclosurereview.governance.persistence.entity.*;
import com.example.disclosurereview.governance.persistence.repository.*;
import com.example.disclosurereview.persistence.entity.LlmCallAttemptEntity;
import com.example.disclosurereview.persistence.repository.LlmCallAttemptJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class GovernanceRunProgressService {
    private final RuleGovernanceRunJpaRepository runRepository;
    private final RuleFeedbackGovernanceGroupJpaRepository groupRepository;
    private final LlmCallAttemptJpaRepository attemptRepository;
    private final GovernanceTraceService traceService;

    public GovernanceRunProgressService(RuleGovernanceRunJpaRepository runRepository,
                                        RuleFeedbackGovernanceGroupJpaRepository groupRepository,
                                        LlmCallAttemptJpaRepository attemptRepository,
                                        GovernanceTraceService traceService) {
        this.runRepository = runRepository; this.groupRepository = groupRepository; this.attemptRepository = attemptRepository;
        this.traceService = traceService;
    }

    @Transactional
    public void refresh(Long runId) {
        RuleGovernanceRunEntity run = runRepository.findById(runId).orElseThrow();
        List<RuleFeedbackGovernanceGroupEntity> groups = groupRepository.findByGovernanceRun_IdOrderById(runId);
        int failed = (int) groups.stream().filter(g -> g.getStatus() == GovernanceGroupStatus.FAILED).count();
        int proposals = (int) groups.stream().filter(g -> g.getStatus() == GovernanceGroupStatus.PROPOSAL_CREATED
                || g.getStatus() == GovernanceGroupStatus.RESOLVED).count();
        int deferred = (int) groups.stream().filter(g -> g.getStatus() == GovernanceGroupStatus.DEFERRED).count();
        boolean active = groups.stream().anyMatch(g -> g.getStatus() == GovernanceGroupStatus.PENDING
                || g.getStatus() == GovernanceGroupStatus.ANALYZING);
        run.setFailedGroupCount(failed); run.setCreatedProposalCount(proposals);
        List<LlmCallAttemptEntity> attempts = attemptRepository.findByGovernanceRunIdOrderById(runId);
        run.setInputTokenCount(attempts.stream().map(LlmCallAttemptEntity::getInputTokenCount).filter(v -> v != null).mapToInt(Integer::intValue).sum());
        run.setOutputTokenCount(attempts.stream().map(LlmCallAttemptEntity::getOutputTokenCount).filter(v -> v != null).mapToInt(Integer::intValue).sum());
        run.setCacheHitTokenCount(attempts.stream().map(LlmCallAttemptEntity::getCacheHitTokenCount).filter(v -> v != null).mapToInt(Integer::intValue).sum());
        if (!active) {
            run.setFinishedAt(Instant.now());
            run.setDurationMs(run.getStartedAt() == null ? null : Duration.between(run.getStartedAt(), run.getFinishedAt()).toMillis());
            run.setStatus(failed > 0 ? (proposals > 0 ? GovernanceRunStatus.PARTIAL_SUCCESS : GovernanceRunStatus.FAILED)
                    : deferred > 0 ? GovernanceRunStatus.PARTIAL_SUCCESS : GovernanceRunStatus.SUCCESS);
        } else {
            run.setStatus(GovernanceRunStatus.RUNNING);
        }
        run.setUpdatedAt(Instant.now()); runRepository.save(run);
        if (!active) traceService.finishRoot(runId, run.getStatus().name(), run.getErrorMessage());
    }
}
