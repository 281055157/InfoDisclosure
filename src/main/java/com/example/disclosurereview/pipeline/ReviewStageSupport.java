package com.example.disclosurereview.pipeline;

import com.example.disclosurereview.model.BusinessAcceptanceDecision;
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
import com.example.disclosurereview.parser.ExcelParameterParser;
import com.example.disclosurereview.parser.FileNameParser;
import com.example.disclosurereview.parser.PdfDocumentParser;
import com.example.disclosurereview.persistence.entity.DocumentPageEntity;
import com.example.disclosurereview.persistence.entity.ExtractedFieldEntity;
import com.example.disclosurereview.persistence.entity.ModelCallRecordEntity;
import com.example.disclosurereview.persistence.entity.ReviewIssueEntity;
import com.example.disclosurereview.persistence.entity.ReviewTaskEntity;
import com.example.disclosurereview.persistence.repository.DocumentPageJpaRepository;
import com.example.disclosurereview.persistence.repository.ExtractedFieldJpaRepository;
import com.example.disclosurereview.persistence.repository.ModelCallRecordJpaRepository;
import com.example.disclosurereview.persistence.repository.ReviewIssueJpaRepository;
import com.example.disclosurereview.persistence.repository.ReviewTaskJpaRepository;
import com.example.disclosurereview.service.AuditLogService;
import com.example.disclosurereview.service.DocumentCategoryResolver;
import com.example.disclosurereview.service.ReviewTaskStateService;
import com.example.disclosurereview.storage.FileStorageService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class ReviewStageSupport {

    private final ReviewTaskJpaRepository taskRepository;
    private final DocumentPageJpaRepository pageRepository;
    private final ExtractedFieldJpaRepository fieldRepository;
    private final ReviewIssueJpaRepository issueRepository;
    private final ModelCallRecordJpaRepository modelCallRepository;
    private final FileStorageService fileStorageService;
    private final PdfDocumentParser pdfParser;
    private final ExcelParameterParser excelParser;
    private final FileNameParser fileNameParser;
    private final ReviewTaskStateService stateService;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final DocumentCategoryResolver documentCategoryResolver;

    public ReviewStageSupport(ReviewTaskJpaRepository taskRepository,
                              DocumentPageJpaRepository pageRepository,
                              ExtractedFieldJpaRepository fieldRepository,
                              ReviewIssueJpaRepository issueRepository,
                              ModelCallRecordJpaRepository modelCallRepository,
                              FileStorageService fileStorageService,
                              PdfDocumentParser pdfParser,
                              ExcelParameterParser excelParser,
                              FileNameParser fileNameParser,
                              ReviewTaskStateService stateService,
                              AuditLogService auditLogService,
                              ObjectMapper objectMapper,
                              MeterRegistry meterRegistry,
                              DocumentCategoryResolver documentCategoryResolver) {
        this.taskRepository = taskRepository;
        this.pageRepository = pageRepository;
        this.fieldRepository = fieldRepository;
        this.issueRepository = issueRepository;
        this.modelCallRepository = modelCallRepository;
        this.fileStorageService = fileStorageService;
        this.pdfParser = pdfParser;
        this.excelParser = excelParser;
        this.fileNameParser = fileNameParser;
        this.stateService = stateService;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.documentCategoryResolver = documentCategoryResolver;
    }

    public ReviewTaskEntity getTask(Long taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + taskId));
    }

    public void transition(Long taskId, ReviewTaskStatus status, String detail) {
        stateService.transition(taskId, status, detail);
    }

    public void updateStage(Long taskId, ReviewStage stage) {
        ReviewTaskEntity task = getTask(taskId);
        ReviewStage before = task.getCurrentStage();
        task.setCurrentStage(stage);
        taskRepository.save(task);
        auditLogService.record(task, "STAGE_CHANGED", "SYSTEM",
                "阶段切换为 " + stage, before == null ? null : before.name(), stage.name());
    }

    public List<DocumentPage> parseAndPersistPages(ReviewTaskEntity task) throws Exception {
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

    public List<DocumentPage> persistedPages(Long taskId) {
        return pageRepository.findByTaskIdOrderByPageNumber(taskId).stream()
                .map(p -> new DocumentPage(p.getPageNumber(), p.getRawText(), p.getNormalizedText()))
                .toList();
    }

    public void resolveAndPersistDeclarations(Long taskId) throws Exception {
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
                task.setTechnicalStatus(com.example.disclosurereview.model.TechnicalStatus.EXCEL_PARSE_FAILED);
                task.setStatusDetail("Excel解析失败，继续PDF审核: " + e.getMessage());
                auditLogService.record(task, "EXCEL_PARSE_FAILED", "SYSTEM", e.getMessage(), null, null);
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

    public void addExtractedField(List<ExtractedFieldEntity> fields,
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
        addField(fields, task, fieldType, fieldValue, normalizedValue, pageNumber, sheetName, cellAddress,
                evidenceText, source, confidence, verified);
    }

    public void saveFields(List<ExtractedFieldEntity> fields) {
        fieldRepository.saveAll(fields);
    }

    public void persistFinalResult(Long taskId, ReviewResult result, Long modelCallId) {
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
        persistIssues(task, result.mergedIssues(), modelCallId);
        persistResultFields(task, result);
        auditLogService.record(task, "RESULT_PERSISTED", "SYSTEM",
                "审核结果已持久化", null, result.technicalStatus().name());
    }

    private BusinessAcceptanceDecision businessAcceptanceFromRisk(com.example.disclosurereview.model.BusinessRisk risk) {
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

    private void persistIssues(ReviewTaskEntity task, List<ReviewIssue> issues, Long modelCallId) {
        issueRepository.deleteByTaskId(task.getId());
        ModelCallRecordEntity modelCall = modelCallId == null
                ? null
                : modelCallRepository.findById(modelCallId).orElse(null);
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
            if ("LLM".equalsIgnoreCase(issue.source())) {
                entity.setModelCall(modelCall);
            }
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

    public void saveTask(ReviewTaskEntity task) {
        taskRepository.save(task);
    }

    public void recordAudit(ReviewTaskEntity task,
                            String operationType,
                            String detail,
                            String beforeValue,
                            String afterValue) {
        auditLogService.record(task, operationType, "SYSTEM", detail, beforeValue, afterValue);
    }

    public void countCompleted() {
        meterRegistry.counter("review_task_completed_total").increment();
    }

    public void countPartialSuccess() {
        meterRegistry.counter("review_task_partial_success_total").increment();
    }

    public String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{}";
        }
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
}
