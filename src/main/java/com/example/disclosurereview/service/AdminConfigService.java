package com.example.disclosurereview.service;

import com.example.disclosurereview.dto.AdminConfigDtos.ModelConfigRequest;
import com.example.disclosurereview.dto.AdminConfigDtos.ModelConfigResponse;
import com.example.disclosurereview.dto.AdminConfigDtos.ModelTestResponse;
import com.example.disclosurereview.dto.AdminConfigDtos.ProviderConfigRequest;
import com.example.disclosurereview.dto.AdminConfigDtos.ProviderConfigResponse;
import com.example.disclosurereview.dto.AdminConfigDtos.ExecutorSchemaResponse;
import com.example.disclosurereview.dto.AdminConfigDtos.RuleConfigRequest;
import com.example.disclosurereview.dto.AdminConfigDtos.RuleConfigResponse;
import com.example.disclosurereview.dto.AdminConfigDtos.RuleDetailResponse;
import com.example.disclosurereview.dto.AdminConfigDtos.RuleExecutionResponse;
import com.example.disclosurereview.dto.AdminConfigDtos.RuleFeedbackResponse;
import com.example.disclosurereview.dto.AdminConfigDtos.RuleTestRequest;
import com.example.disclosurereview.dto.AdminConfigDtos.RuleTestResponse;
import com.example.disclosurereview.dto.AdminConfigDtos.RuleValidationResponse;
import com.example.disclosurereview.dto.AdminConfigDtos.RuleVersionRequest;
import com.example.disclosurereview.dto.AdminConfigDtos.RuleVersionResponse;
import com.example.disclosurereview.llm.LlmGateway;
import com.example.disclosurereview.model.DocumentCategory;
import com.example.disclosurereview.model.DocumentPage;
import com.example.disclosurereview.model.DocumentType;
import com.example.disclosurereview.persistence.entity.LlmModelConfigEntity;
import com.example.disclosurereview.persistence.entity.LlmProviderConfigEntity;
import com.example.disclosurereview.persistence.entity.ReviewRuleDefinitionEntity;
import com.example.disclosurereview.persistence.entity.ReviewRuleExecutionEntity;
import com.example.disclosurereview.persistence.entity.ReviewRuleFeedbackEntity;
import com.example.disclosurereview.persistence.entity.ReviewRuleVersionEntity;
import com.example.disclosurereview.persistence.repository.LlmModelConfigJpaRepository;
import com.example.disclosurereview.persistence.repository.LlmProviderConfigJpaRepository;
import com.example.disclosurereview.persistence.repository.ReviewRuleDefinitionJpaRepository;
import com.example.disclosurereview.persistence.repository.ReviewRuleExecutionJpaRepository;
import com.example.disclosurereview.persistence.repository.ReviewRuleFeedbackJpaRepository;
import com.example.disclosurereview.persistence.repository.ReviewRuleVersionJpaRepository;
import com.example.disclosurereview.rule.domain.RuleExecutionContext;
import com.example.disclosurereview.rule.domain.RuleExecutionResult;
import com.example.disclosurereview.rule.domain.RuleExecutorType;
import com.example.disclosurereview.rule.domain.RuleValidationResult;
import com.example.disclosurereview.rule.domain.RuleVersionStatus;
import com.example.disclosurereview.rule.executor.RuleExecutor;
import com.example.disclosurereview.rule.executor.RuleExecutorRegistry;
import com.example.disclosurereview.rule.executor.RuleJsonSupport;
import com.example.disclosurereview.strategy.DocumentTypeAliasResolver;
import com.example.disclosurereview.util.TextNormalizer;
import com.example.disclosurereview.governance.service.RuleProposalReviewService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
public class AdminConfigService {

    private final ReviewRuleDefinitionJpaRepository ruleRepository;
    private final ReviewRuleVersionJpaRepository ruleVersionRepository;
    private final ReviewRuleExecutionJpaRepository ruleExecutionRepository;
    private final ReviewRuleFeedbackJpaRepository feedbackRepository;
    private final LlmProviderConfigJpaRepository providerRepository;
    private final LlmModelConfigJpaRepository modelRepository;
    private final LlmGateway llmGateway;
    private final RuleExecutorRegistry executorRegistry;
    private final RuleJsonSupport ruleJson;
    private final DocumentTypeAliasResolver documentTypeResolver;
    private final RuleProposalReviewService proposalReviewService;

    public AdminConfigService(ReviewRuleDefinitionJpaRepository ruleRepository,
                              ReviewRuleVersionJpaRepository ruleVersionRepository,
                              ReviewRuleExecutionJpaRepository ruleExecutionRepository,
                              ReviewRuleFeedbackJpaRepository feedbackRepository,
                              LlmProviderConfigJpaRepository providerRepository,
                              LlmModelConfigJpaRepository modelRepository,
                              LlmGateway llmGateway,
                              RuleExecutorRegistry executorRegistry,
                              RuleJsonSupport ruleJson,
                              DocumentTypeAliasResolver documentTypeResolver,
                              RuleProposalReviewService proposalReviewService) {
        this.ruleRepository = ruleRepository;
        this.ruleVersionRepository = ruleVersionRepository;
        this.ruleExecutionRepository = ruleExecutionRepository;
        this.feedbackRepository = feedbackRepository;
        this.providerRepository = providerRepository;
        this.modelRepository = modelRepository;
        this.llmGateway = llmGateway;
        this.executorRegistry = executorRegistry;
        this.ruleJson = ruleJson;
        this.documentTypeResolver = documentTypeResolver;
        this.proposalReviewService = proposalReviewService;
    }

    @Transactional(readOnly = true)
    public List<RuleConfigResponse> rules() {
        return ruleRepository.findAll().stream()
                .map(this::ruleResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public RuleDetailResponse rule(Long id) {
        ReviewRuleDefinitionEntity rule = getRule(id);
        return new RuleDetailResponse(ruleResponse(rule),
                ruleVersionRepository.findByRuleDefinition_IdOrderByVersionNumberDesc(id)
                        .stream().map(this::versionResponse).toList());
    }

    @Transactional
    public RuleConfigResponse createRule(RuleConfigRequest request) {
        ReviewRuleDefinitionEntity entity = new ReviewRuleDefinitionEntity();
        applyRule(entity, request, true);
        Instant now = Instant.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return ruleResponse(ruleRepository.save(entity));
    }

    @Transactional
    public RuleVersionResponse createRuleVersion(Long ruleId, RuleVersionRequest request) {
        ReviewRuleDefinitionEntity rule = getRule(ruleId);
        ReviewRuleVersionEntity entity = new ReviewRuleVersionEntity();
        entity.setRuleDefinition(rule);
        int nextNumber = ruleVersionRepository.findFirstByRuleDefinition_IdOrderByVersionNumberDesc(ruleId)
                .map(v -> v.getVersionNumber() == null ? 1 : v.getVersionNumber() + 1)
                .orElse(1);
        entity.setVersionNumber(nextNumber);
        entity.setVersionCode(storageVersionCode(rule.getRuleCode(), nextNumber));
        entity.setStatus(RuleVersionStatus.DRAFT.name());
        entity.setActive(false);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        applyVersion(entity, request, true);
        return versionResponse(ruleVersionRepository.save(entity));
    }

    @Transactional
    public RuleVersionResponse updateRuleVersion(Long ruleId, Long versionId, RuleVersionRequest request) {
        ReviewRuleVersionEntity entity = getVersion(ruleId, versionId);
        ensureDraft(entity);
        applyVersion(entity, request, false);
        entity.setUpdatedAt(Instant.now());
        return versionResponse(ruleVersionRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public RuleValidationResponse validateRuleVersion(Long ruleId, Long versionId) {
        ReviewRuleVersionEntity version = getVersion(ruleId, versionId);
        RuleValidationResult result = validate(version);
        return new RuleValidationResponse(result.valid(), result.errors());
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public RuleTestResponse testRuleVersion(Long ruleId, Long versionId, RuleTestRequest request) {
        ReviewRuleDefinitionEntity rule = getRule(ruleId);
        ReviewRuleVersionEntity version = getVersion(ruleId, versionId);
        RuleExecutor executor = executor(version);
        String testText = request == null || request.testText() == null ? "" : request.testText();
        DocumentType docType = request == null ? DocumentType.UNKNOWN : documentTypeResolver.resolve(request.documentType());
        DocumentCategory category = parseCategory(request == null ? null : request.documentCategory());
        List<DocumentPage> pages = List.of(new DocumentPage(1, testText, TextNormalizer.normalizePage(testText)));
        RuleExecutionContext context = new RuleExecutionContext(null, pages, "rule-test.pdf",
                category, docType, request == null ? null : request.declaredProductCode(),
                request == null ? null : request.declaredDocumentType(),
                request == null ? null : request.b9Value(), null);
        RuleExecutionResult result;
        try {
            result = executor.execute(context, rule, version);
        } catch (Exception e) {
            result = RuleExecutionResult.failed(e.getMessage());
        }
        return new RuleTestResponse(result.status().name(), result.matched(), result.issues().size(),
                result.productCodeCandidates(), result.productNameCandidates(), result.issues(), result.detail());
    }

    @Transactional
    public RuleVersionResponse publishRuleVersion(Long ruleId, Long versionId) {
        ReviewRuleDefinitionEntity rule = getRule(ruleId);
        ReviewRuleVersionEntity version = getVersion(ruleId, versionId);
        RuleValidationResult validation = validate(version);
        if (!validation.valid()) {
            throw new IllegalArgumentException("规则版本校验失败: " + String.join("; ", validation.errors()));
        }
        Instant now = Instant.now();
        version.setStatus(RuleVersionStatus.PUBLISHED.name());
        version.setActive(true);
        version.setPublishedAt(now);
        version.setUpdatedAt(now);
        ReviewRuleVersionEntity saved = ruleVersionRepository.save(version);
        rule.setActiveVersionId(saved.getId());
        rule.setVersionCode(displayVersionCode(saved));
        rule.setRuleType(defaultText(saved.getExecutorType(), rule.getRuleType()));
        rule.setRuleCategory(categoryFor(saved.getExecutorType()));
        rule.setUpdatedAt(now);
        ruleRepository.save(rule);
        proposalReviewService.markAppliedForRuleVersion(saved.getId(), "demo-user");
        return versionResponse(saved);
    }

    @Transactional
    public RuleConfigResponse updateRule(Long id, RuleConfigRequest request) {
        ReviewRuleDefinitionEntity entity = ruleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Rule not found: " + id));
        applyRule(entity, request, false);
        entity.setUpdatedAt(Instant.now());
        return ruleResponse(ruleRepository.save(entity));
    }

    @Transactional
    public RuleConfigResponse setRuleEnabled(Long id, boolean enabled) {
        ReviewRuleDefinitionEntity entity = ruleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Rule not found: " + id));
        entity.setEnabled(enabled);
        entity.setUpdatedAt(Instant.now());
        return ruleResponse(ruleRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public ExecutorSchemaResponse executorSchemas() {
        return new ExecutorSchemaResponse(executorRegistry.schemas());
    }

    @Transactional(readOnly = true)
    public List<RuleExecutionResponse> ruleExecutions(Long ruleId) {
        return ruleExecutionRepository.findByRuleIdOrderByCreatedAtDesc(ruleId).stream()
                .map(this::executionResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ModelConfigResponse> models() {
        return modelRepository.findAll().stream()
                .map(this::modelResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProviderConfigResponse> providers() {
        return providerRepository.findAll().stream()
                .map(this::providerResponse)
                .toList();
    }

    @Transactional
    public ProviderConfigResponse createProvider(ProviderConfigRequest request) {
        LlmProviderConfigEntity entity = new LlmProviderConfigEntity();
        applyProvider(entity, request, true);
        Instant now = Instant.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return providerResponse(providerRepository.save(entity));
    }

    @Transactional
    public ProviderConfigResponse updateProvider(Long id, ProviderConfigRequest request) {
        LlmProviderConfigEntity entity = providerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Provider not found: " + id));
        applyProvider(entity, request, false);
        entity.setUpdatedAt(Instant.now());
        return providerResponse(providerRepository.save(entity));
    }

    @Transactional
    public ProviderConfigResponse setProviderEnabled(Long id, boolean enabled) {
        LlmProviderConfigEntity entity = providerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Provider not found: " + id));
        entity.setEnabled(enabled);
        entity.setUpdatedAt(Instant.now());
        return providerResponse(providerRepository.save(entity));
    }

    @Transactional
    public ModelConfigResponse createModel(ModelConfigRequest request) {
        LlmModelConfigEntity entity = new LlmModelConfigEntity();
        applyModel(entity, request, true);
        Instant now = Instant.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return modelResponse(modelRepository.save(entity));
    }

    @Transactional
    public ModelConfigResponse updateModel(Long id, ModelConfigRequest request) {
        LlmModelConfigEntity entity = modelRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Model not found: " + id));
        applyModel(entity, request, false);
        entity.setUpdatedAt(Instant.now());
        return modelResponse(modelRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public ModelTestResponse testModel(Long id) {
        try {
            llmGateway.testModel(id);
            return new ModelTestResponse(true, "OK");
        } catch (Exception e) {
            return new ModelTestResponse(false, e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<RuleFeedbackResponse> feedback(Long taskId) {
        List<ReviewRuleFeedbackEntity> rows = taskId == null
                ? feedbackRepository.findAllByOrderByCreatedAtDesc()
                : feedbackRepository.findByTask_IdOrderByCreatedAtDesc(taskId);
        return rows.stream().map(this::feedbackResponse).toList();
    }

    private void applyRule(ReviewRuleDefinitionEntity entity, RuleConfigRequest request, boolean create) {
        if (create || StringUtils.hasText(request.ruleCode())) {
            entity.setRuleCode(required(request.ruleCode(), "ruleCode"));
        }
        if (create || StringUtils.hasText(request.ruleName())) {
            entity.setRuleName(defaultText(request.ruleName(), entity.getRuleCode()));
        }
        if (create || StringUtils.hasText(request.ruleType())) {
            entity.setRuleType(defaultText(request.ruleType(), "JAVA_INFRA"));
        }
        if (request.ruleCategory() != null) {
            entity.setRuleCategory(request.ruleCategory());
        } else if (create) {
            entity.setRuleCategory("JAVA_PLUGIN");
        }
        if (request.priority() != null) {
            entity.setPriority(request.priority());
        } else if (create) {
            entity.setPriority(100);
        }
        if (request.activeVersionId() != null) {
            entity.setActiveVersionId(request.activeVersionId());
        }
        if (request.enabled() != null) {
            entity.setEnabled(request.enabled());
        } else if (create) {
            entity.setEnabled(true);
        }
        if (request.severity() != null) {
            entity.setSeverity(request.severity());
        }
        if (request.confidence() != null) {
            entity.setConfidence(request.confidence());
        }
        if (request.documentTypes() != null) {
            entity.setDocumentTypes(request.documentTypes());
        }
        if (request.productScope() != null) {
            entity.setProductScope(request.productScope());
        }
        if (request.parametersJson() != null) {
            entity.setParametersJson(request.parametersJson());
        }
        if (create || StringUtils.hasText(request.versionCode())) {
            entity.setVersionCode(defaultText(request.versionCode(), "v1"));
        }
    }

    private void applyModel(LlmModelConfigEntity entity, ModelConfigRequest request, boolean create) {
        LlmProviderConfigEntity provider = resolveProvider(request, create ? null : entity.getProvider());
        entity.setProvider(provider);
        if (create || StringUtils.hasText(request.modelCode())) {
            entity.setModelCode(required(request.modelCode(), "modelCode"));
        }
        if (create || StringUtils.hasText(request.modelName())) {
            entity.setModelName(required(request.modelName(), "modelName"));
        }
        entity.setPriority(request.priority() == null ? (create ? 10 : entity.getPriority()) : request.priority());
        entity.setEnabled(request.enabled() == null ? (create || entity.isEnabled()) : request.enabled());
        entity.setTimeoutSeconds(request.timeoutSeconds() == null
                ? (create ? 120 : entity.getTimeoutSeconds())
                : Math.max(1, request.timeoutSeconds()));
        entity.setMaxRetries(request.maxRetries() == null
                ? (create ? 0 : entity.getMaxRetries())
                : Math.max(0, request.maxRetries()));
        entity.setTemperature(request.temperature() == null
                ? (create ? 0.1 : entity.getTemperature())
                : request.temperature());
        entity.setResponseFormat(defaultText(request.responseFormat(),
                create ? "json_object" : entity.getResponseFormat()));
        if (request.apiKeyEnv() != null) {
            entity.setApiKeyEnv(request.apiKeyEnv());
        }
    }

    private LlmProviderConfigEntity resolveProvider(ModelConfigRequest request, LlmProviderConfigEntity current) {
        String providerCode = defaultText(request.providerCode(),
                current == null ? null : current.getProviderCode());
        providerCode = required(providerCode, "providerCode");
        LlmProviderConfigEntity provider = providerRepository.findByProviderCode(providerCode)
                .orElseGet(LlmProviderConfigEntity::new);
        provider.setProviderCode(providerCode);
        provider.setProviderType(defaultText(request.providerType(),
                current == null ? "OPENAI_COMPATIBLE" : current.getProviderType()));
        provider.setBaseUrl(required(defaultText(request.baseUrl(),
                current == null ? null : current.getBaseUrl()), "baseUrl"));
        Instant now = Instant.now();
        if (provider.getCreatedAt() == null) {
            provider.setCreatedAt(now);
            provider.setEnabled(true);
        }
        provider.setUpdatedAt(now);
        return providerRepository.save(provider);
    }

    private void applyProvider(LlmProviderConfigEntity entity, ProviderConfigRequest request, boolean create) {
        if (create || StringUtils.hasText(request.providerCode())) {
            entity.setProviderCode(required(request.providerCode(), "providerCode"));
        }
        if (create || StringUtils.hasText(request.providerType())) {
            entity.setProviderType(defaultText(request.providerType(), "OPENAI_COMPATIBLE"));
        }
        if (create || StringUtils.hasText(request.baseUrl())) {
            entity.setBaseUrl(required(request.baseUrl(), "baseUrl"));
        }
        if (request.enabled() != null) {
            entity.setEnabled(request.enabled());
        } else if (create) {
            entity.setEnabled(true);
        }
    }

    private RuleConfigResponse ruleResponse(ReviewRuleDefinitionEntity entity) {
        return new RuleConfigResponse(entity.getId(), entity.getRuleCode(), entity.getRuleName(),
                entity.getRuleType(), entity.getRuleCategory(), entity.isEnabled(), entity.getSeverity(), entity.getConfidence(),
                entity.getDocumentTypes(), entity.getProductScope(), entity.getParametersJson(),
                displayVersionCode(entity.getVersionCode()), entity.getActiveVersionId(), entity.getPriority(),
                entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private RuleVersionResponse versionResponse(ReviewRuleVersionEntity entity) {
        return new RuleVersionResponse(entity.getId(), displayVersionCode(entity), entity.getVersionNumber(),
                entity.getExecutorType(), entity.getStatus(), entity.isActive(), entity.getDescription(),
                entity.getScopeJson(), entity.getConditionJson(), entity.getActionJson(), entity.getPromptJson(),
                entity.getChangeSummary(), entity.getPublishedAt(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private RuleExecutionResponse executionResponse(ReviewRuleExecutionEntity entity) {
        var task = entity.getTask();
        return new RuleExecutionResponse(entity.getId(),
                task == null ? null : task.getId(),
                task == null ? null : task.getTaskNo(),
                task == null ? null : task.getOriginalFileName(),
                task == null || task.getStatus() == null ? null : task.getStatus().name(),
                task == null ? null : task.getCreatedAt(),
                entity.getRuleCode(), displayVersionCode(entity.getRuleVersion()), entity.getRuleVersionId(),
                entity.getExecutionStatus(), entity.isMatched(), entity.getIssueCount(),
                entity.getDurationMs(), entity.getErrorMessage(), executionDetail(entity), entity.getCreatedAt());
    }

    private String executionDetail(ReviewRuleExecutionEntity entity) {
        String detail = ruleJson.text(ruleJson.read(entity.getResultJson()), "detail", null);
        if (StringUtils.hasText(detail)) return detail;
        if (StringUtils.hasText(entity.getErrorMessage())) return entity.getErrorMessage();
        return switch (entity.getExecutionStatus() == null ? "" : entity.getExecutionStatus()) {
            case "NOT_HIT" -> "规则已执行，未发现满足命中条件的违规内容";
            case "HIT" -> "规则已命中";
            case "INDETERMINATE" -> "规则已执行，但结果无法确定";
            case "SKIPPED" -> "规则执行已跳过";
            default -> null;
        };
    }

    private ModelConfigResponse modelResponse(LlmModelConfigEntity entity) {
        LlmProviderConfigEntity provider = entity.getProvider();
        return new ModelConfigResponse(entity.getId(), provider.getProviderCode(), provider.getProviderType(),
                provider.getBaseUrl(), entity.getModelCode(), entity.getModelName(), entity.getPriority(),
                entity.isEnabled(), entity.getTimeoutSeconds(), entity.getMaxRetries(), entity.getTemperature(),
                entity.getResponseFormat(), entity.getApiKeyEnv(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private ProviderConfigResponse providerResponse(LlmProviderConfigEntity entity) {
        return new ProviderConfigResponse(entity.getId(), entity.getProviderCode(), entity.getProviderType(),
                entity.getBaseUrl(), entity.isEnabled(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private RuleFeedbackResponse feedbackResponse(ReviewRuleFeedbackEntity entity) {
        return new RuleFeedbackResponse(
                entity.getId(),
                entity.getTask() == null ? null : entity.getTask().getId(),
                entity.getIssue() == null ? null : entity.getIssue().getId(),
                entity.getRuleCode(),
                entity.getRuleVersionId(),
                entity.getRuleExecutionId(),
                entity.getFeedbackType(),
                entity.getDocumentCategory(),
                entity.getDeclaredProductCode(),
                entity.getDeclaredDocumentType(),
                entity.getFeedbackSource(),
                entity.getFeedbackTags(),
                entity.getAggregationKey(),
                entity.getProcessStatus(),
                entity.getIssueSnapshotJson(),
                entity.getManualSnapshotJson(),
                entity.getComment(),
                entity.getReviewer(),
                entity.getCreatedAt(),
                entity.getProcessedAt());
    }

    private String required(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.strip();
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.strip() : fallback;
    }

    private String versionCode(Integer versionNumber) {
        return "v" + (versionNumber == null || versionNumber < 1 ? 1 : versionNumber);
    }

    private String storageVersionCode(String ruleCode, Integer versionNumber) {
        String suffix = ":" + versionCode(versionNumber);
        String base = StringUtils.hasText(ruleCode) ? ruleCode.strip() : "RULE";
        if (base.length() + suffix.length() <= 64) {
            return base + suffix;
        }
        String hash = "~" + Integer.toUnsignedString(base.hashCode(), 36);
        int prefixLength = Math.max(1, 64 - suffix.length() - hash.length());
        return base.substring(0, prefixLength) + hash + suffix;
    }

    private String displayVersionCode(ReviewRuleVersionEntity entity) {
        if (entity == null) {
            return null;
        }
        if (entity.getVersionNumber() != null && entity.getVersionNumber() > 0) {
            return versionCode(entity.getVersionNumber());
        }
        return displayVersionCode(entity.getVersionCode());
    }

    private String displayVersionCode(String versionCode) {
        if (!StringUtils.hasText(versionCode)) {
            return versionCode;
        }
        String value = versionCode.strip();
        int idx = value.lastIndexOf(':');
        return idx >= 0 && idx < value.length() - 1 ? value.substring(idx + 1) : value;
    }

    private ReviewRuleDefinitionEntity getRule(Long id) {
        return ruleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Rule not found: " + id));
    }

    private ReviewRuleVersionEntity getVersion(Long ruleId, Long versionId) {
        return ruleVersionRepository.findByIdAndRuleDefinition_Id(versionId, ruleId)
                .orElseThrow(() -> new IllegalArgumentException("Rule version not found: " + versionId));
    }

    private void ensureDraft(ReviewRuleVersionEntity version) {
        if (!RuleVersionStatus.DRAFT.name().equalsIgnoreCase(version.getStatus())) {
            throw new IllegalArgumentException("Only DRAFT rule versions can be modified");
        }
    }

    private void applyVersion(ReviewRuleVersionEntity entity, RuleVersionRequest request, boolean create) {
        if (request == null) {
            request = new RuleVersionRequest(null, null, null, null, null, null, null, null);
        }
        if (request.description() != null) {
            entity.setDescription(request.description());
        }
        if (create || StringUtils.hasText(request.executorType())) {
            entity.setExecutorType(defaultText(request.executorType(), RuleExecutorType.JAVA_PLUGIN.name()));
        }
        if (request.scopeJson() != null) {
            entity.setScopeJson(request.scopeJson());
        } else if (create) {
            entity.setScopeJson("{\"documentCategories\":[],\"documentTypes\":[],\"productCodes\":[],\"productTypes\":[]}");
        }
        if (request.conditionJson() != null) {
            entity.setConditionJson(request.conditionJson());
        } else if (create) {
            entity.setConditionJson("{}");
        }
        if (request.actionJson() != null) {
            entity.setActionJson(request.actionJson());
        } else if (create) {
            entity.setActionJson("{}");
        }
        if (request.promptJson() != null) {
            entity.setPromptJson(request.promptJson());
        } else if (create) {
            entity.setPromptJson("{}");
        }
        if (request.changeSummary() != null) {
            entity.setChangeSummary(request.changeSummary());
        }
        if (request.active() != null) {
            entity.setActive(request.active());
        }
    }

    private RuleValidationResult validate(ReviewRuleVersionEntity version) {
        return executor(version).validate(version);
    }

    private RuleExecutor executor(ReviewRuleVersionEntity version) {
        RuleExecutorType type = ruleJson.executorType(version);
        if (type == null) {
            throw new IllegalArgumentException("executorType is required");
        }
        return executorRegistry.get(type);
    }

    private String categoryFor(String executorType) {
        RuleExecutorType type;
        try {
            type = RuleExecutorType.valueOf(defaultText(executorType, RuleExecutorType.JAVA_PLUGIN.name())
                    .toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            type = RuleExecutorType.JAVA_PLUGIN;
        }
        return switch (type) {
            case HYBRID -> "HYBRID";
            case LLM_POLICY -> "LLM_POLICY";
            case JAVA_PLUGIN -> "JAVA_PLUGIN";
            default -> "HARD_CONFIG";
        };
    }

    private DocumentCategory parseCategory(String value) {
        if (!StringUtils.hasText(value)) {
            return DocumentCategory.AUTO;
        }
        String normalized = "AGREEMENT".equalsIgnoreCase(value) ? "PROTOCOL" : value.strip().toUpperCase(Locale.ROOT);
        try {
            return DocumentCategory.valueOf(normalized);
        } catch (Exception e) {
            return DocumentCategory.AUTO;
        }
    }
}
