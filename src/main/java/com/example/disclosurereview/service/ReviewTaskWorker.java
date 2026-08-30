package com.example.disclosurereview.service;

import com.example.disclosurereview.config.LlmProperties;
import com.example.disclosurereview.model.BusinessAcceptanceDecision;
import com.example.disclosurereview.model.BusinessRisk;
import com.example.disclosurereview.model.DocumentCategory;
import com.example.disclosurereview.model.DocumentPage;
import com.example.disclosurereview.model.ExtractSource;
import com.example.disclosurereview.model.ExcelParameterResult;
import com.example.disclosurereview.model.FileNameInfo;
import com.example.disclosurereview.model.IssueType;
import com.example.disclosurereview.model.ProductIdentityDecision;
import com.example.disclosurereview.model.ReviewIssue;
import com.example.disclosurereview.model.ReviewIssueStatus;
import com.example.disclosurereview.model.ReviewResult;
import com.example.disclosurereview.model.ReviewStage;
import com.example.disclosurereview.model.ReviewTaskStatus;
import com.example.disclosurereview.model.TechnicalStatus;
import com.example.disclosurereview.parser.ExcelParameterParser;
import com.example.disclosurereview.parser.FileNameParser;
import com.example.disclosurereview.parser.PdfDocumentParser;
import com.example.disclosurereview.pipeline.ReviewPipelineContext;
import com.example.disclosurereview.pipeline.ReviewTaskPipeline;
import com.example.disclosurereview.pipeline.StageResult;
import com.example.disclosurereview.persistence.entity.DocumentPageEntity;
import com.example.disclosurereview.persistence.entity.ExtractedFieldEntity;
import com.example.disclosurereview.persistence.entity.ModelCallRecordEntity;
import com.example.disclosurereview.persistence.entity.ReviewIssueEntity;
import com.example.disclosurereview.persistence.entity.ReviewTaskEventEntity;
import com.example.disclosurereview.persistence.entity.ReviewTaskEntity;
import com.example.disclosurereview.persistence.repository.DocumentPageJpaRepository;
import com.example.disclosurereview.persistence.repository.ExtractedFieldJpaRepository;
import com.example.disclosurereview.persistence.repository.ModelCallRecordJpaRepository;
import com.example.disclosurereview.persistence.repository.ReviewIssueJpaRepository;
import com.example.disclosurereview.persistence.repository.ReviewTaskJpaRepository;
import com.example.disclosurereview.storage.FileStorageService;
import com.example.disclosurereview.storage.StoredMultipartFile;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.core.io.Resource;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
public class ReviewTaskWorker {

    private static final Set<ReviewTaskStatus> MANUAL_TERMINAL_STATUSES = EnumSet.of(
            ReviewTaskStatus.MANUAL_APPROVED,
            ReviewTaskStatus.MANUAL_APPROVED_WITH_WARNING,
            ReviewTaskStatus.MANUAL_RETURNED,
            ReviewTaskStatus.MANUAL_REJECTED,
            ReviewTaskStatus.CANCELLED);
    private static final Set<ReviewTaskStatus> RETRYABLE_LLM_STATUSES = EnumSet.of(
            ReviewTaskStatus.WAITING_MANUAL_REVIEW,
            ReviewTaskStatus.PARTIAL_SUCCESS,
            ReviewTaskStatus.FAILED);

    private final ReviewTaskJpaRepository taskRepository;
    private final DocumentPageJpaRepository pageRepository;
    private final ExtractedFieldJpaRepository fieldRepository;
    private final ReviewIssueJpaRepository issueRepository;
    private final ModelCallRecordJpaRepository modelCallRepository;
    private final FileStorageService fileStorageService;
    private final PdfDocumentParser pdfParser;
    private final ExcelParameterParser excelParser;
    private final FileNameParser fileNameParser;
    private final ReviewService reviewService;
    private final ReviewTaskStateService stateService;
    private final AuditLogService auditLogService;
    private final ReviewTaskEventService eventService;
    private final ReviewTaskDispatcher dispatcher;
    private final ObjectMapper objectMapper;
    private final LlmProperties llmProperties;
    private final MeterRegistry meterRegistry;
    private final DocumentCategoryResolver documentCategoryResolver;
    private final ReviewTaskPipeline pipeline;

    public ReviewTaskWorker(ReviewTaskJpaRepository taskRepository,
                            DocumentPageJpaRepository pageRepository,
                            ExtractedFieldJpaRepository fieldRepository,
                            ReviewIssueJpaRepository issueRepository,
                            ModelCallRecordJpaRepository modelCallRepository,
                            FileStorageService fileStorageService,
                            PdfDocumentParser pdfParser,
                            ExcelParameterParser excelParser,
                            FileNameParser fileNameParser,
                            ReviewService reviewService,
                            ReviewTaskStateService stateService,
                            AuditLogService auditLogService,
                            ReviewTaskEventService eventService,
                            ReviewTaskDispatcher dispatcher,
                            ObjectMapper objectMapper,
                            LlmProperties llmProperties,
                            MeterRegistry meterRegistry,
                            DocumentCategoryResolver documentCategoryResolver,
                            ReviewTaskPipeline pipeline) {
        this.taskRepository = taskRepository;
        this.pageRepository = pageRepository;
        this.fieldRepository = fieldRepository;
        this.issueRepository = issueRepository;
        this.modelCallRepository = modelCallRepository;
        this.fileStorageService = fileStorageService;
        this.pdfParser = pdfParser;
        this.excelParser = excelParser;
        this.fileNameParser = fileNameParser;
        this.reviewService = reviewService;
        this.stateService = stateService;
        this.auditLogService = auditLogService;
        this.eventService = eventService;
        this.dispatcher = dispatcher;
        this.objectMapper = objectMapper;
        this.llmProperties = llmProperties;
        this.meterRegistry = meterRegistry;
        this.documentCategoryResolver = documentCategoryResolver;
        this.pipeline = pipeline;
    }

    @RabbitListener(queues = "${review.rabbitmq.stage-queue}")
    public void handleStageMessage(ReviewTaskStageMessage message) {
        ReviewTaskEventEntity event = eventService.markProcessing(message.eventId());
        if (ReviewTaskEventService.STATUS_COMPLETED.equals(event.getEventStatus())) {
            return;
        }
        try {
            StageGuard guard = stageGuard(message, event);
            if (!guard.executable()) {
                completeIgnoredEvent(event, message, guard.reason());
                return;
            }
            executeStage(message);
            eventService.markCompleted(message.eventId());
        } catch (Exception e) {
            if (isObsoleteStateTransition(message, e)) {
                completeIgnoredEvent(event, message, "stale state transition: " + e.getMessage());
                return;
            }
            eventService.markFailed(message.eventId(), e.getMessage());
            failTask(message.taskId(), e);
            throw new IllegalStateException(e);
        }
    }

    private StageGuard stageGuard(ReviewTaskStageMessage message, ReviewTaskEventEntity event) {
        if (!eventService.isEarliestActiveStageEvent(event.getId())) {
            return StageGuard.ignore("same task/stage already has an earlier active event");
        }
        ReviewTaskEntity task = getTask(message.taskId());
        ReviewTaskStatus status = task.getStatus();
        boolean retry = isRetry(message);
        if (isManualTerminal(status)) {
            return StageGuard.ignore("task is in manual terminal status: " + status);
        }
        if (!retry && isCompletedForOrdinaryMessage(status)) {
            return StageGuard.ignore("ordinary stage message is obsolete for status: " + status);
        }
        if (message.stage() == ReviewStage.RESULT_MERGING && status != ReviewTaskStatus.EVIDENCE_VERIFYING) {
            return StageGuard.ignore("RESULT_MERGING requires EVIDENCE_VERIFYING status, actual: " + status);
        }
        if (!stageAllowed(message.stage(), status, retry)) {
            return StageGuard.ignore("stage " + message.stage() + " is not allowed from status " + status
                    + (retry ? " for retry" : ""));
        }
        return StageGuard.execute();
    }

    private boolean stageAllowed(ReviewStage stage, ReviewTaskStatus status, boolean retry) {
        if (stage == null || status == null) {
            return false;
        }
        return switch (stage) {
            case DOCUMENT_PARSING -> status == ReviewTaskStatus.CREATED
                    || status == ReviewTaskStatus.FILE_STORED
                    || status == ReviewTaskStatus.PARSING
                    || (retry && status == ReviewTaskStatus.FAILED);
            case DECLARATION_RESOLVING, PRODUCT_MATCHING -> status == ReviewTaskStatus.PARSING;
            case RULE_REVIEWING -> status == ReviewTaskStatus.PARSING
                    || status == ReviewTaskStatus.RULE_REVIEWING
                    || (retry && status == ReviewTaskStatus.FAILED);
            case LLM_REVIEWING -> status == ReviewTaskStatus.RULE_REVIEWING
                    || status == ReviewTaskStatus.LLM_REVIEWING
                    || (retry && RETRYABLE_LLM_STATUSES.contains(status));
            case EVIDENCE_VERIFYING -> status == ReviewTaskStatus.LLM_REVIEWING;
            case RESULT_MERGING -> status == ReviewTaskStatus.EVIDENCE_VERIFYING;
            case FILE_STORED, WAITING_MANUAL_REVIEW -> false;
        };
    }

    private boolean isObsoleteStateTransition(ReviewTaskStageMessage message, Exception e) {
        if (!(e instanceof IllegalStateException) || e.getMessage() == null
                || !e.getMessage().contains("非法任务状态转换")) {
            return false;
        }
        ReviewTaskEntity task = getTask(message.taskId());
        return !stageAllowed(message.stage(), task.getStatus(), isRetry(message))
                || isManualTerminal(task.getStatus())
                || (!isRetry(message) && isCompletedForOrdinaryMessage(task.getStatus()));
    }

    private void completeIgnoredEvent(ReviewTaskEventEntity event,
                                      ReviewTaskStageMessage message,
                                      String reason) {
        try {
            ReviewTaskEntity task = getTask(message.taskId());
            auditLogService.record(task, "STAGE_MESSAGE_IGNORED", "SYSTEM",
                    "忽略过期或重复阶段消息: " + message.stage() + "，原因: " + reason,
                    task.getCurrentStage() == null ? null : task.getCurrentStage().name(),
                    task.getStatus() == null ? null : task.getStatus().name());
        } catch (Exception ignored) {
            // Ignore audit failure while acknowledging stale queue messages.
        }
        eventService.markCompleted(event.getId());
    }

    private boolean isRetry(ReviewTaskStageMessage message) {
        return "REVIEW_RETRY_REQUESTED".equals(message.eventType());
    }

    private boolean isCompletedForOrdinaryMessage(ReviewTaskStatus status) {
        return status == ReviewTaskStatus.WAITING_MANUAL_REVIEW
                || status == ReviewTaskStatus.PARTIAL_SUCCESS
                || status == ReviewTaskStatus.FAILED
                || status == ReviewTaskStatus.CANCELLED;
    }

    private boolean isManualTerminal(ReviewTaskStatus status) {
        return MANUAL_TERMINAL_STATUSES.contains(status);
    }

    private void executeStage(ReviewTaskStageMessage message) throws Exception {
        boolean retry = "REVIEW_RETRY_REQUESTED".equals(message.eventType());
        Long taskId = message.taskId();
        StageResult result = pipeline.handle(new ReviewPipelineContext(taskId, retry, message.stage()));
        if (!result.terminal()) {
            ReviewStage next = nextStage(message.stage());
            if (next != null) {
                dispatcher.dispatchStage(taskId, next, nextEventType(message), "{}");
            }
        }
    }

    private ReviewStage nextStage(ReviewStage stage) {
        return switch (stage) {
            case DOCUMENT_PARSING -> ReviewStage.DECLARATION_RESOLVING;
            case DECLARATION_RESOLVING -> ReviewStage.PRODUCT_MATCHING;
            case PRODUCT_MATCHING -> ReviewStage.RULE_REVIEWING;
            case RULE_REVIEWING -> ReviewStage.LLM_REVIEWING;
            case LLM_REVIEWING -> ReviewStage.EVIDENCE_VERIFYING;
            case EVIDENCE_VERIFYING -> ReviewStage.RESULT_MERGING;
            case FILE_STORED, RESULT_MERGING, WAITING_MANUAL_REVIEW -> null;
        };
    }

    private String nextEventType(ReviewTaskStageMessage message) {
        return "REVIEW_RETRY_REQUESTED".equals(message.eventType())
                ? "REVIEW_RETRY_REQUESTED"
                : "REVIEW_STAGE_REQUESTED";
    }

    private boolean isLlmDegraded(ReviewResult result) {
        return result.technicalStatus() == TechnicalStatus.LLM_FAILED
                || result.technicalStatus() == TechnicalStatus.LLM_CALL_FAILED
                || result.technicalStatus() == TechnicalStatus.LLM_TIMEOUT
                || result.technicalStatus() == TechnicalStatus.LLM_RESPONSE_INVALID;
    }

    public void process(Long taskId) {
        dispatcher.process(taskId);
    }

    public void retry(Long taskId, ReviewStage stage) {
        dispatcher.retry(taskId, stage);
    }

    private List<DocumentPage> parseAndPersistPages(ReviewTaskEntity task) throws Exception {
        Resource resource = fileStorageService.load(task.getFilePath());
        List<DocumentPage> pages;
        Timer.Sample sample = Timer.start(meterRegistry);
        try (InputStream in = resource.getInputStream()) {
            pages = pdfParser.parse(in);
        } finally {
            sample.stop(meterRegistry.timer("pdf_parse_duration"));
        }
        pageRepository.deleteByTaskId(task.getId());
        Instant now = Instant.now();
        List<DocumentPageEntity> entities = new ArrayList<>();
        for (DocumentPage page : pages) {
            DocumentPageEntity entity = new DocumentPageEntity();
            entity.setTask(task);
            entity.setPageNumber(page.pageNumber());
            entity.setRawText(page.rawText());
            entity.setNormalizedText(page.normalizedText());
            entity.setCharCount(page.normalizedText() == null ? 0 : page.normalizedText().length());
            entity.setCreatedAt(now);
            entities.add(entity);
        }
        pageRepository.saveAll(entities);
        auditLogService.record(task, "PDF_PARSED", "SYSTEM",
                "PDF解析完成，共 " + pages.size() + " 页", null, String.valueOf(pages.size()));
        return pages;
    }

    private void resolveAndPersistDeclarations(Long taskId) throws Exception {
        updateStage(taskId, ReviewStage.DECLARATION_RESOLVING);
        ReviewTaskEntity task = getTask(taskId);
        FileNameInfo fileNameInfo = fileNameParser.parse(task.getOriginalFileName());
        List<ExtractedFieldEntity> fields = new ArrayList<>();
        addField(fields, task, "FILE_NAME_PRODUCT_CODE", fileNameInfo.productCode(), fileNameInfo.productCode(),
                null, null, null, task.getOriginalFileName(), ExtractSource.FILE_NAME, 1.0, true);
        addField(fields, task, "FILE_NAME_DOCUMENT_TYPE", fileNameInfo.declaredDocumentType(),
                fileNameInfo.declaredDocumentType(), null, null, null, task.getOriginalFileName(),
                ExtractSource.FILE_NAME, 1.0, true);

        String b9Value = null;
        if (StringUtils.hasText(task.getParameterFilePath())) {
            try (InputStream in = fileStorageService.load(task.getParameterFilePath()).getInputStream()) {
                ExcelParameterResult b9 = excelParser.parseB9(in);
                b9Value = b9.normalizedValue();
                addField(fields, task, "B9_DOCUMENT_TYPE", b9.rawValue(), b9.normalizedValue(), null,
                        b9.sheetName(), b9.cellAddress(), b9.rawValue(), ExtractSource.EXCEL_B9, 1.0,
                        StringUtils.hasText(b9.normalizedValue()));
            } catch (Exception e) {
                task.setTechnicalStatus(TechnicalStatus.EXCEL_PARSE_FAILED);
                task.setStatusDetail("Excel解析失败，继续PDF审核: " + e.getMessage());
                auditLogService.record(task, "EXCEL_PARSE_FAILED", "SYSTEM",
                        e.getMessage(), null, null);
            }
        }

        DocumentCategory category = documentCategoryResolver.resolve(task.getDocumentCategory(), b9Value);
        String effectiveCode = StringUtils.hasText(task.getDeclaredProductCode())
                ? task.getDeclaredProductCode()
                : fileNameInfo.productCode();
        String effectiveType = StringUtils.hasText(task.getDeclaredDocumentType())
                ? task.getDeclaredDocumentType()
                : (category == DocumentCategory.ANNOUNCEMENT && StringUtils.hasText(b9Value)
                ? b9Value
                : fileNameInfo.declaredDocumentType());

        task.setDocumentCategory(category);
        task.setDeclaredProductCode(effectiveCode);
        task.setDeclaredDocumentType(effectiveType);
        task.setB9Value(b9Value);
        taskRepository.save(task);
        fieldRepository.saveAll(fields);
        auditLogService.record(task, "DECLARATION_RESOLVED", "SYSTEM",
                "声明信息解析完成", null, effectiveCode + " / " + effectiveType);
    }

    private ReviewResult runSynchronousEngine(ReviewTaskEntity task) throws Exception {
        DocumentCategory resolvedCategory = documentCategoryResolver.resolve(
                task.getDocumentCategory(), task.getB9Value());
        if (resolvedCategory != task.getDocumentCategory()) {
            task.setDocumentCategory(resolvedCategory);
            taskRepository.save(task);
            auditLogService.record(task, "DOCUMENT_CATEGORY_RESOLVED", "SYSTEM",
                    "文件类别已从 AUTO 更新为 " + resolvedCategory.name(), "AUTO", resolvedCategory.name());
        }
        Path pdfPath = fileStorageService.load(task.getFilePath()).getFile().toPath();
        MultipartFile pdfFile = new StoredMultipartFile(pdfPath, task.getOriginalFileName(), "file");
        MultipartFile parameterFile = null;
        if (StringUtils.hasText(task.getParameterFilePath())) {
            Path parameterPath = fileStorageService.load(task.getParameterFilePath()).getFile().toPath();
            parameterFile = new StoredMultipartFile(parameterPath, "parameter.xlsx", "parameterFile");
        }
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            return reviewService.review(pdfFile, parameterFile, task.getDocumentCategory(),
                    task.getDeclaredProductCode(), task.getDeclaredDocumentType(), task.getId());
        } finally {
            sample.stop(meterRegistry.timer("llm_call_duration"));
        }
    }

    private void persistReviewResult(Long taskId, ReviewResult result, List<DocumentPage> pages) {
        ReviewTaskEntity task = getTask(taskId);
        task.setTechnicalStatus(result.technicalStatus());
        task.setBusinessRisk(result.businessRisk());
        task.setStatusDetail(result.statusDetail());
        if (result.declaredInfo() != null) {
            task.setDeclaredProductCode(result.declaredInfo().productCode());
            task.setDeclaredDocumentType(result.declaredInfo().documentType());
            task.setB9Value(result.declaredInfo().b9Value());
        }
        if (result.targetProductAssessment() != null) {
            task.setProductIdentityDecision(result.targetProductAssessment().productIdentityDecision());
            task.setBusinessAcceptanceDecision(result.targetProductAssessment().businessAcceptanceDecision());
        } else {
            task.setProductIdentityDecision(ProductIdentityDecision.PRODUCT_NOT_IDENTIFIED);
            task.setBusinessAcceptanceDecision(businessAcceptanceFromRisk(result.businessRisk()));
        }
        task.setResultJson(toJson(result));
        taskRepository.save(task);

        persistModelCall(task, result, pages);
        persistIssues(task, result.mergedIssues());
        persistResultFields(task, result);
        auditLogService.record(task, "RESULT_PERSISTED", "SYSTEM",
                "审核结果已持久化", null, result.technicalStatus().name());
    }

    private BusinessAcceptanceDecision businessAcceptanceFromRisk(BusinessRisk risk) {
        if (risk == null) {
            return BusinessAcceptanceDecision.UNKNOWN;
        }
        return switch (risk) {
            case HIGH -> BusinessAcceptanceDecision.MANUAL_REVIEW;
            case MEDIUM, LOW -> BusinessAcceptanceDecision.ACCEPTABLE_WITH_WARNING;
            case NORMAL -> BusinessAcceptanceDecision.ACCEPTABLE;
            case UNKNOWN -> BusinessAcceptanceDecision.UNKNOWN;
        };
    }

    private void persistModelCall(ReviewTaskEntity task, ReviewResult result, List<DocumentPage> pages) {
        ModelCallRecordEntity record = new ModelCallRecordEntity();
        record.setTask(task);
        record.setStage(ReviewStage.LLM_REVIEWING.name());
        record.setProvider("OPENAI_COMPATIBLE");
        record.setModelName(llmProperties.getModel());
        record.setPromptVersion(task.getReviewVersion());
        record.setRuleVersion(task.getReviewVersion());
        record.setRequestSummary("pages=" + pages.size() + ", chars="
                + pages.stream().mapToInt(p -> p.normalizedText() == null ? 0 : p.normalizedText().length()).sum());
        record.setStructuredResponse(result.llmResult() == null ? null : toJson(result.llmResult()));
        record.setInputCharCount(pages.stream().mapToInt(p -> p.normalizedText() == null ? 0 : p.normalizedText().length()).sum());
        record.setCallStatus(result.technicalStatus() == TechnicalStatus.LLM_FAILED ? "FAILED" : "SUCCESS");
        record.setErrorMessage(result.technicalStatus() == TechnicalStatus.LLM_FAILED ? result.statusDetail() : null);
        record.setCreatedAt(Instant.now());
        modelCallRepository.save(record);
    }

    private void persistIssues(ReviewTaskEntity task, List<ReviewIssue> issues) {
        issueRepository.deleteByTaskId(task.getId());
        Instant now = Instant.now();
        List<ReviewIssueEntity> entities = new ArrayList<>();
        for (ReviewIssue issue : issues == null ? List.<ReviewIssue>of() : issues) {
            ReviewIssueEntity entity = new ReviewIssueEntity();
            entity.setTask(task);
            IssueType issueType = issue.issueType() == null ? IssueType.UNKNOWN_ISSUE : issue.issueType();
            entity.setIssueCode(issueType.name());
            entity.setIssueName(issueName(issueType));
            entity.setSeverity(issue.severity());
            entity.setConfidence(issue.confidence());
            entity.setPageNumber(issue.pageNumber());
            entity.setEvidenceText(issue.evidenceText());
            entity.setEvidenceVerified(Boolean.TRUE.equals(issue.verified()));
            entity.setExplanation(issue.explanation());
            entity.setSuggestion(issue.suggestion());
            entity.setSourceType(issue.source());
            entity.setRuleCode(issue.ruleCode());
            entity.setRuleVersionId(issue.ruleVersionId());
            entity.setRuleExecutionId(issue.executionId());
            entity.setIssueStatus(ReviewIssueStatus.OPEN);
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            entities.add(entity);
        }
        issueRepository.saveAll(entities);
    }

    private void persistResultFields(ReviewTaskEntity task, ReviewResult result) {
        List<ExtractedFieldEntity> fields = new ArrayList<>();
        if (result.productMaster() != null && result.productMaster().matched()) {
            addField(fields, task, "PRODUCT_MASTER_CODE", result.productMaster().productCode(),
                    result.productMaster().productCode(), null, null, null,
                    result.productMaster().productName(), ExtractSource.PRODUCT_MASTER, 1.0, true);
            addField(fields, task, "PRODUCT_MASTER_NAME", result.productMaster().productName(),
                    result.productMaster().productName(), null, null, null,
                    result.productMaster().productName(), ExtractSource.PRODUCT_MASTER, 1.0, true);
        }
        if (result.ruleResult() != null) {
            result.ruleResult().productCodeCandidates().forEach(v -> addField(fields, task,
                    "RULE_PRODUCT_CODE", v.value(), v.value(), v.pageNumber(), null, null,
                    v.evidenceText(), ExtractSource.RULE, 1.0, true));
            result.ruleResult().productNameCandidates().forEach(v -> addField(fields, task,
                    "RULE_PRODUCT_NAME", v.value(), v.value(), v.pageNumber(), null, null,
                    v.evidenceText(), ExtractSource.RULE, 1.0, true));
        }
        fieldRepository.saveAll(fields);
    }

    private void addField(List<ExtractedFieldEntity> fields,
                          ReviewTaskEntity task,
                          String fieldType,
                          String fieldValue,
                          String normalizedValue,
                          Integer pageNumber,
                          String sheetName,
                          String cellAddress,
                          String evidenceText,
                          ExtractSource source,
                          Double confidence,
                          boolean verified) {
        if (!StringUtils.hasText(fieldValue) && !StringUtils.hasText(evidenceText)) {
            return;
        }
        ExtractedFieldEntity entity = new ExtractedFieldEntity();
        entity.setTask(task);
        entity.setFieldType(fieldType);
        entity.setFieldValue(fieldValue);
        entity.setNormalizedValue(normalizedValue);
        entity.setPageNumber(pageNumber);
        entity.setSheetName(sheetName);
        entity.setCellAddress(cellAddress);
        entity.setEvidenceText(evidenceText);
        entity.setExtractSource(source);
        entity.setConfidence(confidence);
        entity.setVerified(verified);
        entity.setCreatedAt(Instant.now());
        fields.add(entity);
    }

    private void updateStage(Long taskId, ReviewStage stage) {
        ReviewTaskEntity task = getTask(taskId);
        ReviewStage before = task.getCurrentStage();
        task.setCurrentStage(stage);
        taskRepository.save(task);
        auditLogService.record(task, "STAGE_CHANGED", "SYSTEM",
                "阶段切换为 " + stage, before == null ? null : before.name(), stage.name());
    }

    private void failTask(Long taskId, Exception e) {
        ReviewTaskEntity task = getTask(taskId);
        task.setTechnicalStatus(technicalStatus(e));
        task.setStatusDetail(e.getMessage());
        taskRepository.save(task);
        try {
            stateService.transition(taskId, ReviewTaskStatus.FAILED, "审核任务失败: " + e.getMessage());
        } catch (Exception transitionError) {
            task.setStatus(ReviewTaskStatus.FAILED);
            task.setCompletedAt(Instant.now());
            taskRepository.save(task);
            auditLogService.record(task, "TASK_FAILED", "SYSTEM", e.getMessage(), null, null);
        }
    }

    private List<DocumentPage> persistedPages(Long taskId) {
        return pageRepository.findByTaskIdOrderByPageNumber(taskId).stream()
                .map(p -> new DocumentPage(p.getPageNumber(), p.getRawText(), p.getNormalizedText()))
                .toList();
    }

    private TechnicalStatus technicalStatus(Exception e) {
        String name = e.getClass().getSimpleName();
        if (name.contains("PdfEncrypted")) {
            return TechnicalStatus.PDF_ENCRYPTED;
        }
        if (name.contains("Pdf")) {
            return TechnicalStatus.PDF_PARSE_FAILED;
        }
        if (name.contains("Excel")) {
            return TechnicalStatus.EXCEL_PARSE_FAILED;
        }
        return TechnicalStatus.UNKNOWN_ERROR;
    }

    private ReviewTaskEntity getTask(Long taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + taskId));
    }

    private String issueName(IssueType type) {
        return switch (type) {
            case CONTENT_PRODUCT_CODE_CONFLICT -> "产品代码冲突";
            case CONTENT_PRODUCT_NAME_CONFLICT -> "产品名称冲突";
            case CONTENT_LOGIC_CONFLICT -> "正文逻辑冲突";
            case DECLARED_PRODUCT_NOT_FOUND -> "声明产品未找到";
            case DECLARED_TYPE_MISMATCH -> "声明类型不匹配";
            case POSSIBLE_TEMPLATE_RESIDUE -> "可能的模板残留";
            case PRODUCT_NAME_VARIANT -> "产品名称变体";
            case PRODUCT_REFERENCE -> "产品引用";
            case UNKNOWN_ISSUE -> "未知问题";
        };
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private record StageGuard(boolean executable, String reason) {
        private static StageGuard execute() {
            return new StageGuard(true, null);
        }

        private static StageGuard ignore(String reason) {
            return new StageGuard(false, reason);
        }
    }
}
