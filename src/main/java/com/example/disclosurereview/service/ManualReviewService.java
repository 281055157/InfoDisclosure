package com.example.disclosurereview.service;

import com.example.disclosurereview.config.ReviewProperties;
import com.example.disclosurereview.dto.ReviewTaskDtos.IssueUpdateRequest;
import com.example.disclosurereview.dto.ReviewTaskDtos.ManualIssueRequest;
import com.example.disclosurereview.dto.ReviewTaskDtos.ManualReviewRequest;
import com.example.disclosurereview.model.BusinessAcceptanceDecision;
import com.example.disclosurereview.model.IssueType;
import com.example.disclosurereview.model.ManualReviewDecision;
import com.example.disclosurereview.model.ProductIdentityDecision;
import com.example.disclosurereview.model.ReviewIssue;
import com.example.disclosurereview.model.ReviewIssueStatus;
import com.example.disclosurereview.model.ReviewStage;
import com.example.disclosurereview.model.ReviewTaskStatus;
import com.example.disclosurereview.persistence.entity.ManualReviewEntity;
import com.example.disclosurereview.persistence.entity.ReviewIssueEntity;
import com.example.disclosurereview.persistence.entity.ReviewRuleFeedbackEntity;
import com.example.disclosurereview.persistence.entity.ReviewTaskEntity;
import com.example.disclosurereview.persistence.repository.ManualReviewJpaRepository;
import com.example.disclosurereview.persistence.repository.ReviewIssueJpaRepository;
import com.example.disclosurereview.persistence.repository.ReviewRuleFeedbackJpaRepository;
import com.example.disclosurereview.persistence.repository.ReviewTaskJpaRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ManualReviewService {

    private final ReviewTaskJpaRepository taskRepository;
    private final ManualReviewJpaRepository manualReviewRepository;
    private final ReviewIssueJpaRepository issueRepository;
    private final ReviewRuleFeedbackJpaRepository feedbackRepository;
    private final ReviewTaskStateService stateService;
    private final AuditLogService auditLogService;
    private final ReviewTaskDispatcher dispatcher;
    private final ReviewProperties properties;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    public ManualReviewService(ReviewTaskJpaRepository taskRepository,
                               ManualReviewJpaRepository manualReviewRepository,
                               ReviewIssueJpaRepository issueRepository,
                               ReviewRuleFeedbackJpaRepository feedbackRepository,
                               ReviewTaskStateService stateService,
                               AuditLogService auditLogService,
                               ReviewTaskDispatcher dispatcher,
                               ReviewProperties properties,
                               ObjectMapper objectMapper,
                               MeterRegistry meterRegistry) {
        this.taskRepository = taskRepository;
        this.manualReviewRepository = manualReviewRepository;
        this.issueRepository = issueRepository;
        this.feedbackRepository = feedbackRepository;
        this.stateService = stateService;
        this.auditLogService = auditLogService;
        this.dispatcher = dispatcher;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public void submitManualReview(Long taskId, ManualReviewRequest request) {
        ReviewTaskEntity task = getTask(taskId);
        ManualReviewDecision decision = request.decision() == null
                ? ManualReviewDecision.UNABLE_TO_CONFIRM
                : request.decision();
        ManualReviewEntity manual = new ManualReviewEntity();
        manual.setTask(task);
        manual.setReviewDecision(decision);
        manual.setReviewComment(request.comment());
        manual.setAiResultCorrect(request.aiResultCorrect());
        manual.setContainsFalsePositive(request.containsFalsePositive());
        manual.setContainsFalseNegative(request.containsFalseNegative());
        manual.setActualIssueTypes(toJson(request.actualIssueTypes() == null ? List.of() : request.actualIssueTypes()));
        manual.setReviewer(StringUtils.hasText(request.reviewer()) ? request.reviewer().strip() : "demo-user");
        manual.setReviewedAt(Instant.now());
        manualReviewRepository.save(manual);
        if (Boolean.TRUE.equals(request.containsFalsePositive())) {
            saveFeedback(task, null, null, "FALSE_POSITIVE", request.comment(), manual.getReviewer());
        }
        if (Boolean.TRUE.equals(request.containsFalseNegative())) {
            saveFeedback(task, null, null, "FALSE_NEGATIVE", request.comment(), manual.getReviewer());
        }

        if (task.getStatus() == ReviewTaskStatus.PARTIAL_SUCCESS
                || task.getStatus() == ReviewTaskStatus.MANUAL_APPROVED
                || task.getStatus() == ReviewTaskStatus.MANUAL_APPROVED_WITH_WARNING
                || task.getStatus() == ReviewTaskStatus.MANUAL_RETURNED
                || task.getStatus() == ReviewTaskStatus.MANUAL_REJECTED) {
            stateService.transition(taskId, ReviewTaskStatus.WAITING_MANUAL_REVIEW, "重新进入人工审核确认");
        }
        task = getTask(taskId);
        task.setManualReviewedAt(manual.getReviewedAt());
        task.setBusinessAcceptanceDecision(acceptanceFrom(decision));
        task.setProductIdentityDecision(task.getProductIdentityDecision() == null
                ? ProductIdentityDecision.PRODUCT_NOT_IDENTIFIED
                : task.getProductIdentityDecision());
        taskRepository.save(task);
        stateService.transition(taskId, statusFrom(decision), "人工审核提交: " + decision);
        auditLogService.record(getTask(taskId), "MANUAL_REVIEW_SUBMITTED", manual.getReviewer(),
                request.comment(), null, decision.name());
        meterRegistry.counter("manual_review_total").increment();
    }

    @Transactional
    public void updateIssue(Long taskId, Long issueId, IssueUpdateRequest request) {
        ReviewIssueEntity issue = issueRepository.findByIdAndTaskId(issueId, taskId)
                .orElseThrow(() -> new IllegalArgumentException("问题不存在: " + issueId));
        ReviewIssueStatus before = issue.getIssueStatus();
        ReviewIssueStatus after = request.issueStatus() == null ? ReviewIssueStatus.OPEN : request.issueStatus();
        if (after == ReviewIssueStatus.FALSE_POSITIVE) {
            ensureFalsePositiveFeedbackEditable(issue);
        }
        issue.setIssueStatus(after);
        issue.setUpdatedAt(Instant.now());
        issueRepository.save(issue);
        if (after == ReviewIssueStatus.FALSE_POSITIVE) {
            // issueCode describes the finding category; ruleCode identifies the rule that produced it.
            // Governance groups and rule versions must always use the latter.
            saveFeedback(issue.getTask(), issue, issue.getRuleCode(), "FALSE_POSITIVE",
                    request.comment(), "demo-user");
        }
        auditLogService.record(issue.getTask(), "ISSUE_STATUS_UPDATED", "demo-user",
                request.comment(), before.name(), after.name());
    }

    @Transactional
    public void addManualIssue(Long taskId, ManualIssueRequest request) {
        ReviewTaskEntity task = getTask(taskId);
        ReviewIssue issue = request.issue();
        ReviewIssueEntity entity = new ReviewIssueEntity();
        entity.setTask(task);
        IssueType type = issue == null || issue.issueType() == null ? IssueType.UNKNOWN_ISSUE : issue.issueType();
        entity.setIssueCode(type.name());
        entity.setIssueName(type.name());
        entity.setSeverity(issue == null ? null : issue.severity());
        entity.setConfidence(issue == null ? 1.0 : issue.confidence());
        entity.setPageNumber(issue == null ? null : issue.pageNumber());
        entity.setEvidenceText(issue == null ? null : issue.evidenceText());
        entity.setEvidenceVerified(issue != null && Boolean.TRUE.equals(issue.verified()));
        entity.setExplanation(issue == null ? request.comment() : issue.explanation());
        entity.setSuggestion(issue == null ? null : issue.suggestion());
        entity.setSourceType("MANUAL");
        entity.setIssueStatus(ReviewIssueStatus.CONFIRMED);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(entity.getCreatedAt());
        ReviewIssueEntity saved = issueRepository.save(entity);
        saveFeedback(task, saved, saved.getIssueCode(), "FALSE_NEGATIVE",
                request.comment(), StringUtils.hasText(request.reviewer()) ? request.reviewer() : "demo-user");
        auditLogService.record(task, "MANUAL_ISSUE_ADDED",
                StringUtils.hasText(request.reviewer()) ? request.reviewer() : "demo-user",
                request.comment(), null, type.name());
    }

    @Transactional
    public void reopen(Long taskId) {
        ReviewTaskEntity task = getTask(taskId);
        if (task.getStatus() != ReviewTaskStatus.WAITING_MANUAL_REVIEW) {
            stateService.transition(taskId, ReviewTaskStatus.WAITING_MANUAL_REVIEW, "重新打开人工审核");
        }
        auditLogService.record(getTask(taskId), "TASK_REOPENED", "demo-user", "任务已重新打开", null, null);
    }

    @Transactional
    public void retry(Long taskId, ReviewStage stage) {
        ReviewTaskEntity task = getTask(taskId);
        if (task.getRetryCount() >= properties.getRetry().getMaxAttempts()) {
            if (task.getStatus() == ReviewTaskStatus.FAILED || task.getStatus() == ReviewTaskStatus.PARTIAL_SUCCESS) {
                task.setStatus(ReviewTaskStatus.WAITING_MANUAL_REVIEW);
                taskRepository.save(task);
            }
            auditLogService.record(task, "RETRY_REJECTED", "SYSTEM",
                    "超过最大重试次数，转人工审核", String.valueOf(task.getRetryCount()),
                    String.valueOf(properties.getRetry().getMaxAttempts()));
            return;
        }
        ReviewStage retryStage = stage == null ? ReviewStage.LLM_REVIEWING : stage;
        boolean dispatched = dispatcher.retry(taskId, retryStage);
        if (!dispatched) {
            auditLogService.record(task, "RETRY_DEDUPED", "SYSTEM",
                    "同一阶段已有活跃重试事件，忽略重复点击: " + retryStage.name(),
                    String.valueOf(task.getRetryCount()), String.valueOf(task.getRetryCount()));
            return;
        }
        task.setRetryCount(task.getRetryCount() + 1);
        taskRepository.save(task);
        auditLogService.record(task, "TASK_RETRY", "SYSTEM",
                "从阶段重试: " + retryStage.name(),
                String.valueOf(task.getRetryCount() - 1), String.valueOf(task.getRetryCount()));
    }

    private ReviewTaskStatus statusFrom(ManualReviewDecision decision) {
        return switch (decision) {
            case APPROVED -> ReviewTaskStatus.MANUAL_APPROVED;
            case APPROVED_WITH_WARNING, UNABLE_TO_CONFIRM -> ReviewTaskStatus.MANUAL_APPROVED_WITH_WARNING;
            case RETURNED -> ReviewTaskStatus.MANUAL_RETURNED;
            case REJECTED -> ReviewTaskStatus.MANUAL_REJECTED;
        };
    }

    private BusinessAcceptanceDecision acceptanceFrom(ManualReviewDecision decision) {
        return switch (decision) {
            case APPROVED -> BusinessAcceptanceDecision.ACCEPTABLE;
            case APPROVED_WITH_WARNING, UNABLE_TO_CONFIRM -> BusinessAcceptanceDecision.ACCEPTABLE_WITH_WARNING;
            case RETURNED -> BusinessAcceptanceDecision.MANUAL_REVIEW;
            case REJECTED -> BusinessAcceptanceDecision.REJECT_SUGGESTED;
        };
    }

    private ReviewTaskEntity getTask(Long taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + taskId));
    }

    private void saveFeedback(ReviewTaskEntity task,
                              ReviewIssueEntity issue,
                              String ruleCode,
                              String feedbackType,
                              String comment,
                              String reviewer) {
        ReviewRuleFeedbackEntity feedback = reusableFeedback(task, issue, feedbackType);
        feedback.setRuleCode(ruleCode);
        feedback.setRuleVersionId(issue == null ? null : issue.getRuleVersionId());
        feedback.setRuleExecutionId(issue == null ? null : issue.getRuleExecutionId());
        feedback.setFeedbackType(feedbackType);
        feedback.setDocumentCategory(task.getDocumentCategory() == null ? null : task.getDocumentCategory().name());
        feedback.setDeclaredProductCode(task.getDeclaredProductCode());
        feedback.setDeclaredDocumentType(task.getDeclaredDocumentType());
        feedback.setFeedbackSource(issue == null ? "MANUAL_REVIEW" : "ISSUE_REVIEW");
        feedback.setFeedbackTags(toJson(List.of(feedbackType)));
        feedback.setAggregationKey(aggregationKey(task, issue, ruleCode, feedbackType));
        if (!StringUtils.hasText(feedback.getProcessStatus())) {
            feedback.setProcessStatus("PENDING");
        }
        feedback.setIssueSnapshotJson(toJson(issueSnapshot(issue)));
        feedback.setManualSnapshotJson(toJson(Map.of(
                "feedbackType", feedbackType == null ? "" : feedbackType,
                "comment", comment == null ? "" : comment,
                "reviewer", StringUtils.hasText(reviewer) ? reviewer.strip() : "demo-user")));
        feedback.setComment(comment);
        feedback.setReviewer(StringUtils.hasText(reviewer) ? reviewer.strip() : "demo-user");
        feedback.setCreatedAt(Instant.now());
        feedbackRepository.save(feedback);
    }

    private ReviewRuleFeedbackEntity reusableFeedback(ReviewTaskEntity task,
                                                      ReviewIssueEntity issue,
                                                      String feedbackType) {
        if (issue != null && StringUtils.hasText(feedbackType)) {
            return feedbackRepository.findFirstByIssue_IdAndFeedbackTypeOrderByCreatedAtDesc(issue.getId(), feedbackType)
                    .map(existing -> {
                        if (isProcessed(existing.getProcessStatus())) {
                            throw new IllegalStateException("误报反馈已处理，不能修改");
                        }
                        return existing;
                    })
                    .orElseGet(() -> newFeedback(task, issue));
        }
        return newFeedback(task, issue);
    }

    private ReviewRuleFeedbackEntity newFeedback(ReviewTaskEntity task, ReviewIssueEntity issue) {
        ReviewRuleFeedbackEntity feedback = new ReviewRuleFeedbackEntity();
        feedback.setTask(task);
        feedback.setIssue(issue);
        return feedback;
    }

    private void ensureFalsePositiveFeedbackEditable(ReviewIssueEntity issue) {
        feedbackRepository.findFirstByIssue_IdAndFeedbackTypeOrderByCreatedAtDesc(issue.getId(), "FALSE_POSITIVE")
                .ifPresent(feedback -> {
                    if (isProcessed(feedback.getProcessStatus())) {
                        throw new IllegalStateException("误报反馈已处理，不能修改");
                    }
                });
    }

    private boolean isProcessed(String processStatus) {
        return StringUtils.hasText(processStatus)
                && !"NEW".equalsIgnoreCase(processStatus)
                && !"PENDING".equalsIgnoreCase(processStatus);
    }

    private String aggregationKey(ReviewTaskEntity task,
                                  ReviewIssueEntity issue,
                                  String ruleCode,
                                  String feedbackType) {
        return String.join("|",
                nullToEmpty(ruleCode),
                nullToEmpty(issue == null ? null : issue.getRuleVersionId()),
                nullToEmpty(feedbackType),
                nullToEmpty(task.getDocumentCategory() == null ? null : task.getDocumentCategory().name()),
                nullToEmpty(task.getDeclaredDocumentType()),
                nullToEmpty(issue == null ? null : issue.getIssueCode()));
    }

    private Map<String, Object> issueSnapshot(ReviewIssueEntity issue) {
        if (issue == null) {
            return Map.of();
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("issueId", issue.getId());
        snapshot.put("issueCode", nullToEmpty(issue.getIssueCode()));
        snapshot.put("issueName", nullToEmpty(issue.getIssueName()));
        snapshot.put("severity", issue.getSeverity() == null ? "" : issue.getSeverity().name());
        snapshot.put("confidence", issue.getConfidence() == null ? "" : issue.getConfidence());
        snapshot.put("pageNumber", issue.getPageNumber() == null ? "" : issue.getPageNumber());
        snapshot.put("evidenceText", nullToEmpty(issue.getEvidenceText()));
        snapshot.put("explanation", nullToEmpty(issue.getExplanation()));
        snapshot.put("suggestion", nullToEmpty(issue.getSuggestion()));
        snapshot.put("sourceType", nullToEmpty(issue.getSourceType()));
        snapshot.put("ruleCode", nullToEmpty(issue.getRuleCode()));
        snapshot.put("ruleVersionId", issue.getRuleVersionId() == null ? "" : issue.getRuleVersionId());
        snapshot.put("ruleExecutionId", issue.getRuleExecutionId() == null ? "" : issue.getRuleExecutionId());
        return snapshot;
    }

    private String nullToEmpty(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }
}
