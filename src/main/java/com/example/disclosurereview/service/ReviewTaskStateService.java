package com.example.disclosurereview.service;

import com.example.disclosurereview.model.ReviewTaskStatus;
import com.example.disclosurereview.persistence.entity.ReviewTaskEntity;
import com.example.disclosurereview.persistence.repository.ReviewTaskJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Service
public class ReviewTaskStateService {

    private final ReviewTaskJpaRepository taskRepository;
    private final AuditLogService auditLogService;
    private final Map<ReviewTaskStatus, Set<ReviewTaskStatus>> allowed = new EnumMap<>(ReviewTaskStatus.class);

    public ReviewTaskStateService(ReviewTaskJpaRepository taskRepository, AuditLogService auditLogService) {
        this.taskRepository = taskRepository;
        this.auditLogService = auditLogService;
        allow(ReviewTaskStatus.CREATED, ReviewTaskStatus.FILE_STORED, ReviewTaskStatus.PARSING,
                ReviewTaskStatus.FAILED, ReviewTaskStatus.CANCELLED);
        allow(ReviewTaskStatus.FILE_STORED, ReviewTaskStatus.PARSING, ReviewTaskStatus.FAILED);
        allow(ReviewTaskStatus.PARSING, ReviewTaskStatus.RULE_REVIEWING, ReviewTaskStatus.FAILED);
        allow(ReviewTaskStatus.RULE_REVIEWING, ReviewTaskStatus.LLM_REVIEWING, ReviewTaskStatus.FAILED);
        allow(ReviewTaskStatus.LLM_REVIEWING, ReviewTaskStatus.EVIDENCE_VERIFYING,
                ReviewTaskStatus.PARTIAL_SUCCESS, ReviewTaskStatus.FAILED);
        allow(ReviewTaskStatus.EVIDENCE_VERIFYING, ReviewTaskStatus.RESULT_MERGING,
                ReviewTaskStatus.PARTIAL_SUCCESS, ReviewTaskStatus.FAILED);
        allow(ReviewTaskStatus.RESULT_MERGING, ReviewTaskStatus.WAITING_MANUAL_REVIEW,
                ReviewTaskStatus.PARTIAL_SUCCESS, ReviewTaskStatus.FAILED);
        allow(ReviewTaskStatus.WAITING_MANUAL_REVIEW, ReviewTaskStatus.MANUAL_APPROVED,
                ReviewTaskStatus.MANUAL_APPROVED_WITH_WARNING, ReviewTaskStatus.MANUAL_RETURNED,
                ReviewTaskStatus.MANUAL_REJECTED, ReviewTaskStatus.CANCELLED,
                ReviewTaskStatus.LLM_REVIEWING);
        allow(ReviewTaskStatus.PARTIAL_SUCCESS, ReviewTaskStatus.LLM_REVIEWING,
                ReviewTaskStatus.WAITING_MANUAL_REVIEW, ReviewTaskStatus.FAILED);
        allow(ReviewTaskStatus.FAILED, ReviewTaskStatus.PARSING, ReviewTaskStatus.RULE_REVIEWING,
                ReviewTaskStatus.LLM_REVIEWING);
        allow(ReviewTaskStatus.MANUAL_APPROVED, ReviewTaskStatus.WAITING_MANUAL_REVIEW);
        allow(ReviewTaskStatus.MANUAL_APPROVED_WITH_WARNING, ReviewTaskStatus.WAITING_MANUAL_REVIEW);
        allow(ReviewTaskStatus.MANUAL_RETURNED, ReviewTaskStatus.WAITING_MANUAL_REVIEW);
        allow(ReviewTaskStatus.MANUAL_REJECTED, ReviewTaskStatus.WAITING_MANUAL_REVIEW);
    }

    @Transactional
    public ReviewTaskEntity transition(Long taskId, ReviewTaskStatus to, String detail) {
        ReviewTaskEntity task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + taskId));
        return transition(task, to, detail);
    }

    public ReviewTaskEntity transition(ReviewTaskEntity task, ReviewTaskStatus to, String detail) {
        ReviewTaskStatus from = task.getStatus();
        if (from == to) {
            return task;
        }
        if (!allowed.getOrDefault(from, Set.of()).contains(to)) {
            throw new IllegalStateException("非法任务状态转换: " + from + " -> " + to);
        }
        task.setStatus(to);
        Instant now = Instant.now();
        if (task.getStartedAt() == null && to != ReviewTaskStatus.CREATED) {
            task.setStartedAt(now);
        }
        if (isAiFlowComplete(to) && task.getCompletedAt() == null) {
            task.setCompletedAt(now);
        }
        if (isTerminal(to)) {
            task.setCompletedAt(now);
        }
        ReviewTaskEntity saved = taskRepository.save(task);
        auditLogService.record(saved, "STATUS_CHANGED", "SYSTEM", detail,
                from == null ? null : from.name(), to.name());
        return saved;
    }

    private boolean isTerminal(ReviewTaskStatus status) {
        return status == ReviewTaskStatus.MANUAL_APPROVED
                || status == ReviewTaskStatus.MANUAL_APPROVED_WITH_WARNING
                || status == ReviewTaskStatus.MANUAL_RETURNED
                || status == ReviewTaskStatus.MANUAL_REJECTED
                || status == ReviewTaskStatus.FAILED
                || status == ReviewTaskStatus.CANCELLED;
    }

    private boolean isAiFlowComplete(ReviewTaskStatus status) {
        return status == ReviewTaskStatus.WAITING_MANUAL_REVIEW
                || status == ReviewTaskStatus.PARTIAL_SUCCESS;
    }

    private void allow(ReviewTaskStatus from, ReviewTaskStatus... targets) {
        allowed.put(from, EnumSet.copyOf(java.util.Arrays.asList(targets)));
    }
}
