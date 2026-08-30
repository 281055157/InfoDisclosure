package com.example.disclosurereview.governance.service;

import com.example.disclosurereview.governance.domain.*;
import com.example.disclosurereview.governance.messaging.GovernanceDispatcher;
import com.example.disclosurereview.governance.persistence.entity.*;
import com.example.disclosurereview.governance.persistence.repository.*;
import com.example.disclosurereview.service.AuditLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class FeedbackGovernanceRunService {
    private final RuleGovernanceRunJpaRepository runRepository;
    private final RuleFeedbackGovernanceGroupJpaRepository groupRepository;
    private final FeedbackGovernanceGroupService groupService;
    private final GovernanceDispatcher dispatcher;
    private final AuditLogService auditLogService;
    private final GovernanceTraceService traceService;

    public FeedbackGovernanceRunService(RuleGovernanceRunJpaRepository runRepository,
                                        RuleFeedbackGovernanceGroupJpaRepository groupRepository,
                                        FeedbackGovernanceGroupService groupService,
                                        GovernanceDispatcher dispatcher,
                                        AuditLogService auditLogService,
                                        GovernanceTraceService traceService) {
        this.runRepository = runRepository; this.groupRepository = groupRepository;
        this.groupService = groupService; this.dispatcher = dispatcher; this.auditLogService = auditLogService;
        this.traceService = traceService;
    }

    @Transactional
    public RuleGovernanceRunEntity start(GovernanceRunTriggerType trigger, String operator) {
        Instant now = Instant.now();
        RuleGovernanceRunEntity run = new RuleGovernanceRunEntity();
        run.setRunNo(runNo(now)); run.setTriggerType(trigger); run.setStatus(GovernanceRunStatus.RUNNING);
        run.setTraceId("governance-" + UUID.randomUUID());
        run.setStartedAt(now); run.setCreatedAt(now); run.setUpdatedAt(now);
        run = runRepository.save(run);
        traceService.ensureRoot(run);
        FeedbackGovernanceGroupService.GroupingResult grouped = groupService.createGroups(run);
        run.setScannedFeedbackCount(grouped.scannedFeedbackCount());
        run.setCreatedGroupCount(grouped.groups().size());
        run.setSkippedFeedbackCount(grouped.skippedFeedbackCount());
        run.setSkipReasonSummary(grouped.skippedReasons().entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .reduce((left, right) -> left + "; " + right).orElse(null));
        traceService.instant(run.getId(), null, "FEEDBACK_SCAN", "扫描并聚合反馈", "SUCCESS",
                "SERIAL", null, java.util.Map.of(
                        "scannedFeedbackCount", grouped.scannedFeedbackCount(),
                        "createdGroupCount", grouped.groups().size(),
                        "skippedFeedbackCount", grouped.skippedFeedbackCount()));
        if (grouped.groups().isEmpty()) {
            if (grouped.scannedFeedbackCount() == 0 && !org.springframework.util.StringUtils.hasText(run.getSkipReasonSummary())) {
                run.setSkipReasonSummary("NO_ELIGIBLE_FEEDBACK=没有 NEW/PENDING 且尚未归组的反馈；FAILED/DEFERRED 分组请在“治理分组”中重新分析");
            }
            run.setStatus(GovernanceRunStatus.SUCCESS); run.setFinishedAt(Instant.now()); run.setDurationMs(0L);
            traceService.instant(run.getId(), null, "OUTCOME", "没有可聚合反馈", "NO_OP",
                    "SERIAL", null, java.util.Map.of("nextAction", "前往治理分组重试 FAILED/DEFERRED 分组"));
            traceService.finishRoot(run.getId(), "SUCCESS", null);
        }
        run.setUpdatedAt(Instant.now()); runRepository.save(run);
        for (RuleFeedbackGovernanceGroupEntity group : grouped.groups()) dispatcher.dispatch(run.getId(), group.getId());
        auditLogService.recordGovernance(run.getId(), null, null, null, null,
                "GOVERNANCE_RUN_STARTED", operator, "扫描反馈并创建治理分组",
                null, "groups=" + grouped.groups().size());
        return run;
    }

    @Transactional
    public void retryGroup(Long groupId, String operator) {
        RuleFeedbackGovernanceGroupEntity group = groupRepository.findLockedById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("治理分组不存在: " + groupId));
        if (group.getStatus() != GovernanceGroupStatus.FAILED && group.getStatus() != GovernanceGroupStatus.DEFERRED
                && group.getStatus() != GovernanceGroupStatus.PENDING) {
            throw new IllegalStateException("分组当前不可重新分析: " + group.getStatus());
        }
        RuleGovernanceRunEntity run = group.getGovernanceRun();
        run.setStatus(GovernanceRunStatus.RUNNING); run.setFinishedAt(null); run.setErrorMessage(null); run.setUpdatedAt(Instant.now());
        runRepository.save(run);
        traceService.reopenRoot(run.getId());
        group.setStatus(GovernanceGroupStatus.PENDING); group.setErrorMessage(null); group.setUpdatedAt(Instant.now()); groupRepository.save(group);
        dispatcher.dispatch(run.getId(), groupId);
        auditLogService.recordGovernance(run.getId(), groupId, null, group.getRuleCode(), null,
                "GOVERNANCE_GROUP_RETRY", operator, "人工重新触发治理分析", null, GovernanceGroupStatus.PENDING.name());
    }

    private String runNo(Instant now) {
        return "RGR-" + DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC).format(now)
                + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
