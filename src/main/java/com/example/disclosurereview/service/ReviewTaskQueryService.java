package com.example.disclosurereview.service;

import com.example.disclosurereview.dto.ReviewTaskDtos.IssueResponse;
import com.example.disclosurereview.dto.ReviewTaskDtos.IssueFeedbackResponse;
import com.example.disclosurereview.dto.ReviewTaskDtos.LlmCallResponse;
import com.example.disclosurereview.dto.ReviewTaskDtos.LlmUsageResponse;
import com.example.disclosurereview.dto.ReviewTaskDtos.ManualReviewResponse;
import com.example.disclosurereview.dto.ReviewTaskDtos.PageTextResponse;
import com.example.disclosurereview.dto.ReviewTaskDtos.ReviewReportResponse;
import com.example.disclosurereview.dto.ReviewTaskDtos.StatisticsSummaryResponse;
import com.example.disclosurereview.dto.ReviewTaskDtos.TaskDetailResponse;
import com.example.disclosurereview.dto.ReviewTaskDtos.TaskSummaryResponse;
import com.example.disclosurereview.dto.ReviewTaskDtos.TimelineEntryResponse;
import com.example.disclosurereview.model.BusinessRisk;
import com.example.disclosurereview.model.DecisionEvidence;
import com.example.disclosurereview.model.DocumentCategory;
import com.example.disclosurereview.model.EvidenceSource;
import com.example.disclosurereview.model.ReviewIssueStatus;
import com.example.disclosurereview.model.ReviewResult;
import com.example.disclosurereview.model.ReviewTaskStatus;
import com.example.disclosurereview.model.TechnicalStatus;
import com.example.disclosurereview.persistence.entity.AuditLogEntity;
import com.example.disclosurereview.persistence.entity.DocumentPageEntity;
import com.example.disclosurereview.persistence.entity.ExtractedFieldEntity;
import com.example.disclosurereview.persistence.entity.LlmCallAttemptEntity;
import com.example.disclosurereview.persistence.entity.ManualReviewEntity;
import com.example.disclosurereview.persistence.entity.ReviewIssueEntity;
import com.example.disclosurereview.persistence.entity.ReviewRuleFeedbackEntity;
import com.example.disclosurereview.persistence.entity.ReviewTaskEntity;
import com.example.disclosurereview.persistence.repository.DocumentPageJpaRepository;
import com.example.disclosurereview.persistence.repository.ExtractedFieldJpaRepository;
import com.example.disclosurereview.persistence.repository.LlmCallAttemptJpaRepository;
import com.example.disclosurereview.persistence.repository.ManualReviewJpaRepository;
import com.example.disclosurereview.persistence.repository.ModelCallRecordJpaRepository;
import com.example.disclosurereview.persistence.repository.ReviewIssueJpaRepository;
import com.example.disclosurereview.persistence.repository.ReviewRuleFeedbackJpaRepository;
import com.example.disclosurereview.persistence.repository.ReviewTaskJpaRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ReviewTaskQueryService {

    private final ReviewTaskJpaRepository taskRepository;
    private final DocumentPageJpaRepository pageRepository;
    private final ReviewIssueJpaRepository issueRepository;
    private final ExtractedFieldJpaRepository fieldRepository;
    private final ManualReviewJpaRepository manualReviewRepository;
    private final LlmCallAttemptJpaRepository llmCallAttemptRepository;
    private final ModelCallRecordJpaRepository modelCallRepository;
    private final ReviewRuleFeedbackJpaRepository feedbackRepository;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    public ReviewTaskQueryService(ReviewTaskJpaRepository taskRepository,
                                  DocumentPageJpaRepository pageRepository,
                                  ReviewIssueJpaRepository issueRepository,
                                  ExtractedFieldJpaRepository fieldRepository,
                                  ManualReviewJpaRepository manualReviewRepository,
                                  LlmCallAttemptJpaRepository llmCallAttemptRepository,
                                  ModelCallRecordJpaRepository modelCallRepository,
                                  ReviewRuleFeedbackJpaRepository feedbackRepository,
                                  AuditLogService auditLogService,
                                  ObjectMapper objectMapper) {
        this.taskRepository = taskRepository;
        this.pageRepository = pageRepository;
        this.issueRepository = issueRepository;
        this.fieldRepository = fieldRepository;
        this.manualReviewRepository = manualReviewRepository;
        this.llmCallAttemptRepository = llmCallAttemptRepository;
        this.modelCallRepository = modelCallRepository;
        this.feedbackRepository = feedbackRepository;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public Page<TaskSummaryResponse> list(String keyword,
                                          ReviewTaskStatus status,
                                          TechnicalStatus technicalStatus,
                                          BusinessRisk businessRisk,
                                          DocumentCategory documentCategory,
                                          String documentType,
                                          String manualReviewStatus,
                                          Instant createdFrom,
                                          Instant createdTo,
                                          Pageable pageable) {
        Specification<ReviewTaskEntity> spec = Specification.where(null);
        if (StringUtils.hasText(keyword)) {
            String key = "%" + keyword.strip().toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("taskNo")), key),
                    cb.like(cb.lower(root.get("originalFileName")), key),
                    cb.like(cb.lower(root.get("declaredProductCode")), key),
                    cb.like(cb.lower(root.get("declaredDocumentType")), key)));
        }
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (technicalStatus != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("technicalStatus"), technicalStatus));
        }
        if (businessRisk != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("businessRisk"), businessRisk));
        }
        if (documentCategory != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("documentCategory"), documentCategory));
        }
        if (StringUtils.hasText(documentType)) {
            String type = "%" + documentType.strip() + "%";
            spec = spec.and((root, query, cb) -> cb.like(root.get("declaredDocumentType"), type));
        }
        if (createdFrom != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), createdFrom));
        }
        if (createdTo != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), createdTo));
        }
        if (StringUtils.hasText(manualReviewStatus)) {
            String v = manualReviewStatus.strip().toUpperCase(Locale.ROOT);
            if ("PENDING".equals(v)) {
                spec = spec.and((root, query, cb) -> cb.isNull(root.get("manualReviewedAt")));
            } else if ("REVIEWED".equals(v)) {
                spec = spec.and((root, query, cb) -> cb.isNotNull(root.get("manualReviewedAt")));
            }
        }
        return taskRepository.findAll(spec, pageable).map(this::summary);
    }

    @Transactional(readOnly = true)
    public TaskDetailResponse detail(Long taskId) {
        ReviewTaskEntity task = getTask(taskId);
        return detail(task);
    }

    @Transactional(readOnly = true)
    public ReviewReportResponse report(Long taskId) {
        ReviewTaskEntity task = getTask(taskId);
        ReviewResult result = reviewResult(task);
        Map<Long, ReviewRuleFeedbackEntity> falsePositiveFeedback = falsePositiveFeedbackByIssue(taskId);
        List<IssueResponse> issues = issueRepository.findByTaskIdOrderById(taskId).stream()
                .map(issue -> issue(issue, falsePositiveFeedback.get(issue.getId())))
                .toList();
        List<DecisionEvidence> evidence = evidenceChain(taskId);
        List<ManualReviewResponse> manualReviews = manualReviewRepository.findByTaskIdOrderByReviewedAtDesc(taskId)
                .stream().map(this::manualReview).toList();
        String summary = result == null || result.llmResult() == null ? "" : result.llmResult().summary();
        String suggestion = result == null || result.llmResult() == null ? "" : result.llmResult().manualReviewSuggestion();
        return new ReviewReportResponse(task.getId(), task.getTaskNo(), result, issues, evidence,
                manualReviews, summary, suggestion);
    }

    @Transactional(readOnly = true)
    public List<PageTextResponse> pages(Long taskId) {
        return pageRepository.findByTaskIdOrderByPageNumber(taskId).stream()
                .map(this::page)
                .toList();
    }

    @Transactional(readOnly = true)
    public PageTextResponse page(Long taskId, int pageNumber) {
        return pageRepository.findByTaskIdAndPageNumber(taskId, pageNumber)
                .map(this::page)
                .orElseThrow(() -> new IllegalArgumentException("页面不存在: " + pageNumber));
    }

    @Transactional(readOnly = true)
    public List<TimelineEntryResponse> timeline(Long taskId) {
        return auditLogService.timeline(taskId).stream().map(this::timeline).toList();
    }

    @Transactional(readOnly = true)
    public LlmUsageResponse llmUsage(Long taskId) {
        getTask(taskId);
        Usage usage = usage(taskId);
        return new LlmUsageResponse(taskId, usage.inputTokens(), usage.outputTokens(),
                usage.cacheHitTokens(), usage.callCount());
    }

    @Transactional(readOnly = true)
    public List<LlmCallResponse> llmCalls(Long taskId) {
        getTask(taskId);
        return llmCallAttemptRepository.findByTask_IdOrderById(taskId).stream()
                .map(this::llmCall)
                .toList();
    }

    @Transactional(readOnly = true)
    public StatisticsSummaryResponse statistics() {
        long total = taskRepository.count();
        long waiting = taskRepository.countByStatus(ReviewTaskStatus.WAITING_MANUAL_REVIEW);
        long high = taskRepository.countByBusinessRisk(BusinessRisk.HIGH);
        long partial = taskRepository.countByStatus(ReviewTaskStatus.PARTIAL_SUCCESS);
        Instant today = LocalDate.now(ZoneId.systemDefault()).atStartOfDay(ZoneId.systemDefault()).toInstant();
        long completedToday = taskRepository.countByCompletedAtGreaterThanEqual(today);
        List<ReviewTaskEntity> tasks = taskRepository.findAll();
        double avg = tasks.stream()
                .filter(t -> t.getStartedAt() != null && t.getCompletedAt() != null)
                .mapToLong(t -> Duration.between(t.getStartedAt(), t.getCompletedAt()).toSeconds())
                .average()
                .orElse(0.0);
        return new StatisticsSummaryResponse(total, waiting, high, partial, completedToday, avg);
    }

    private TaskDetailResponse detail(ReviewTaskEntity task) {
        long pageCount = pageRepository.findByTaskIdOrderByPageNumber(task.getId()).size();
        long openIssueCount = issueRepository.countByTaskIdAndIssueStatus(task.getId(), ReviewIssueStatus.OPEN);
        return new TaskDetailResponse(task.getId(), task.getTaskNo(), task.getOriginalFileName(),
                task.getDocumentCategory(), task.getDeclaredProductCode(), task.getDeclaredDocumentType(),
                task.getB9Value(), task.getStatus(), task.getTechnicalStatus(), task.getBusinessRisk(),
                task.getProductIdentityDecision(), task.getBusinessAcceptanceDecision(),
                task.getCurrentStage() == null ? null : task.getCurrentStage().name(),
                task.getStatusDetail(), task.getReviewVersion(), task.getRetryCount(), task.getCreatedAt(),
                task.getStartedAt(), task.getCompletedAt(), task.getManualReviewedAt(), pageCount,
                openIssueCount, reviewResult(task));
    }

    private TaskSummaryResponse summary(ReviewTaskEntity task) {
        Usage usage = usage(task.getId());
        return new TaskSummaryResponse(task.getId(), task.getTaskNo(), task.getOriginalFileName(),
                task.getDocumentCategory(), task.getDeclaredProductCode(), task.getDeclaredDocumentType(),
                task.getStatus(), task.getTechnicalStatus(), task.getBusinessRisk(),
                task.getProductIdentityDecision(), task.getBusinessAcceptanceDecision(),
                task.getCurrentStage() == null ? null : task.getCurrentStage().name(), task.getRetryCount(),
                usage.inputTokens(), usage.outputTokens(), usage.cacheHitTokens(),
                task.getCreatedAt(), task.getCompletedAt(), task.getManualReviewedAt());
    }

    private PageTextResponse page(DocumentPageEntity page) {
        return new PageTextResponse(page.getTask().getId(), page.getPageNumber(), page.getRawText(),
                page.getNormalizedText(), page.getCharCount());
    }

    private IssueResponse issue(ReviewIssueEntity issue) {
        return issue(issue, null);
    }

    private IssueResponse issue(ReviewIssueEntity issue, ReviewRuleFeedbackEntity falsePositiveFeedback) {
        String falsePositiveStatus = falsePositiveStatus(issue, falsePositiveFeedback);
        return new IssueResponse(issue.getId(), issue.getIssueCode(), issue.getIssueName(),
                issue.getSeverity() == null ? null : issue.getSeverity().name(), issue.getConfidence(),
                issue.getPageNumber(), issue.getEvidenceText(), issue.isEvidenceVerified(),
                issue.getExplanation(), issue.getSuggestion(), issue.getSourceType(),
                issue.getRuleCode(), issue.getRuleVersionId(), issue.getRuleExecutionId(),
                issue.getIssueStatus(), falsePositiveStatus, feedback(falsePositiveFeedback),
                issue.getCreatedAt(), issue.getUpdatedAt());
    }

    private Map<Long, ReviewRuleFeedbackEntity> falsePositiveFeedbackByIssue(Long taskId) {
        if (feedbackRepository == null) {
            return Map.of();
        }
        return feedbackRepository.findByTask_IdOrderByCreatedAtDesc(taskId).stream()
                .filter(feedback -> feedback.getIssue() != null)
                .filter(feedback -> "FALSE_POSITIVE".equals(feedback.getFeedbackType()))
                .collect(Collectors.toMap(
                        feedback -> feedback.getIssue().getId(),
                        Function.identity(),
                        (latest, ignored) -> latest));
    }

    private String falsePositiveStatus(ReviewIssueEntity issue, ReviewRuleFeedbackEntity feedback) {
        if (feedback != null && isProcessed(feedback.getProcessStatus())) {
            return "PROCESSED";
        }
        if (feedback != null || issue.getIssueStatus() == ReviewIssueStatus.FALSE_POSITIVE) {
            return "MARKED";
        }
        return "UNMARKED";
    }

    private boolean isProcessed(String processStatus) {
        return "PROCESSED".equalsIgnoreCase(processStatus)
                || "RESOLVED".equalsIgnoreCase(processStatus)
                || "DONE".equalsIgnoreCase(processStatus);
    }

    private IssueFeedbackResponse feedback(ReviewRuleFeedbackEntity feedback) {
        if (feedback == null) {
            return null;
        }
        return new IssueFeedbackResponse(
                feedback.getId(),
                feedback.getFeedbackType(),
                feedback.getProcessStatus(),
                feedback.getComment(),
                feedback.getReviewer(),
                feedback.getManualSnapshotJson(),
                feedback.getCreatedAt(),
                feedback.getProcessedAt());
    }

    private TimelineEntryResponse timeline(AuditLogEntity log) {
        return new TimelineEntryResponse(log.getId(), log.getOperationType(), log.getOperator(),
                log.getOperationDetail(), log.getBeforeValue(), log.getAfterValue(),
                log.getTraceId(), log.getCreatedAt());
    }

    private LlmCallResponse llmCall(LlmCallAttemptEntity record) {
        return new LlmCallResponse(
                record.getId(),
                record.getStage(),
                record.getOperationType(),
                record.getProviderCode(),
                record.getModelName(),
                record.getRuleCode(),
                record.getRuleVersionId(),
                record.getChunkIndex(),
                record.getPageFrom(),
                record.getPageTo(),
                record.getInputTokenCount(),
                record.getOutputTokenCount(),
                record.getCacheHitTokenCount(),
                record.getDurationMs(),
                record.getCallStatus(),
                record.getErrorMessage(),
                record.getCreatedAt());
    }

    private Usage usage(Long taskId) {
        List<LlmCallAttemptEntity> calls = llmCallAttemptRepository.findByTask_IdOrderById(taskId);
        long input = calls.stream().mapToLong(c -> value(c.getInputTokenCount())).sum();
        long output = calls.stream().mapToLong(c -> value(c.getOutputTokenCount())).sum();
        long cacheHit = calls.stream().mapToLong(c -> value(c.getCacheHitTokenCount())).sum();
        return new Usage(input, output, cacheHit, calls.size());
    }

    private long value(Integer value) {
        return value == null ? 0L : value;
    }

    private ManualReviewResponse manualReview(ManualReviewEntity manual) {
        return new ManualReviewResponse(manual.getId(), manual.getReviewDecision(),
                manual.getReviewComment(), manual.getAiResultCorrect(), manual.getContainsFalsePositive(),
                manual.getContainsFalseNegative(), readStringList(manual.getActualIssueTypes()),
                manual.getReviewer(), manual.getReviewedAt());
    }

    private List<DecisionEvidence> evidenceChain(Long taskId) {
        List<DecisionEvidence> evidence = new java.util.ArrayList<>();
        for (ExtractedFieldEntity field : fieldRepository.findByTaskIdOrderById(taskId)) {
            EvidenceSource source = switch (field.getExtractSource()) {
                case FILE_NAME -> EvidenceSource.FILE_NAME;
                case EXCEL_B9 -> EvidenceSource.EXCEL_B9;
                case PRODUCT_MASTER -> EvidenceSource.PRODUCT_MASTER;
                case LLM -> EvidenceSource.LLM;
                case MANUAL -> EvidenceSource.MANUAL;
                case RULE -> EvidenceSource.RULE;
            };
            evidence.add(new DecisionEvidence("FIELD-" + field.getId(), source, field.getFieldType(),
                    field.getFieldValue(), field.getPageNumber(), field.getSheetName(), field.getCellAddress(),
                    field.getEvidenceText(), field.isVerified(), field.getConfidence(), null, null));
        }
        for (ReviewIssueEntity issue : issueRepository.findByTaskIdOrderById(taskId)) {
            evidence.add(new DecisionEvidence("ISSUE-" + issue.getId(), EvidenceSource.RULE,
                    issue.getIssueCode(), issue.getIssueName(), issue.getPageNumber(),
                    issue.getSheetName(), issue.getCellAddress(), issue.getEvidenceText(),
                    issue.isEvidenceVerified(), issue.getConfidence(), issue.getRuleCode(),
                    issue.getModelCall() == null ? null : issue.getModelCall().getId()));
        }
        return evidence;
    }

    private ReviewResult reviewResult(ReviewTaskEntity task) {
        if (!StringUtils.hasText(task.getResultJson())) {
            return null;
        }
        try {
            return objectMapper.readValue(task.getResultJson(), ReviewResult.class);
        } catch (Exception e) {
            return null;
        }
    }

    private List<String> readStringList(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    private ReviewTaskEntity getTask(Long taskId) {
        return taskRepository.findById(Objects.requireNonNull(taskId, "taskId"))
                .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + taskId));
    }

    private record Usage(long inputTokens, long outputTokens, long cacheHitTokens, long callCount) {
    }
}
