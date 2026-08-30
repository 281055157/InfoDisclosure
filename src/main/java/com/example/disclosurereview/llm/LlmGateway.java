package com.example.disclosurereview.llm;

import com.example.disclosurereview.config.LlmProperties;
import com.example.disclosurereview.exception.LlmException;
import com.example.disclosurereview.governance.service.GovernanceTraceService;
import com.example.disclosurereview.persistence.entity.LlmCallAttemptEntity;
import com.example.disclosurereview.persistence.entity.LlmModelConfigEntity;
import com.example.disclosurereview.persistence.entity.ModelCallRecordEntity;
import com.example.disclosurereview.persistence.entity.ReviewTaskEntity;
import com.example.disclosurereview.persistence.repository.LlmCallAttemptJpaRepository;
import com.example.disclosurereview.persistence.repository.LlmModelConfigJpaRepository;
import com.example.disclosurereview.persistence.repository.ModelCallRecordJpaRepository;
import com.example.disclosurereview.persistence.repository.ReviewTaskJpaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Component
public class LlmGateway {

    private static final Logger log = LoggerFactory.getLogger(LlmGateway.class);

    private final List<LlmProviderAdapter> adapters;
    private final LlmProperties properties;
    private final LlmModelConfigJpaRepository modelRepository;
    private final LlmCallAttemptJpaRepository attemptRepository;
    private final ReviewTaskJpaRepository taskRepository;
    private final ModelCallRecordJpaRepository modelCallRepository;
    private final ObjectMapper objectMapper;
    private final GovernanceTraceService traceService;

    @Autowired
    public LlmGateway(List<LlmProviderAdapter> adapters,
                      LlmProperties properties,
                      LlmModelConfigJpaRepository modelRepository,
                      LlmCallAttemptJpaRepository attemptRepository,
                      ReviewTaskJpaRepository taskRepository,
                      ModelCallRecordJpaRepository modelCallRepository,
                      ObjectMapper objectMapper,
                      GovernanceTraceService traceService) {
        this.adapters = adapters == null ? List.of() : adapters;
        this.properties = properties;
        this.modelRepository = modelRepository;
        this.attemptRepository = attemptRepository;
        this.taskRepository = taskRepository;
        this.modelCallRepository = modelCallRepository;
        this.objectMapper = objectMapper;
        this.traceService = traceService;
    }

    public LlmGateway(List<LlmProviderAdapter> adapters,
                      LlmProperties properties,
                      LlmModelConfigJpaRepository modelRepository,
                      LlmCallAttemptJpaRepository attemptRepository,
                      ReviewTaskJpaRepository taskRepository,
                      ModelCallRecordJpaRepository modelCallRepository,
                      ObjectMapper objectMapper) {
        this(adapters, properties, modelRepository, attemptRepository, taskRepository,
                modelCallRepository, objectMapper, null);
    }

    public LlmGateway(LlmClient client, LlmProperties properties) {
        this(List.of(new LegacyClientAdapter(client)), properties, null, null, null, null, new ObjectMapper(), null);
    }

    public String chatCompletion(String systemPrompt, String userPrompt) {
        return chatCompletion(systemPrompt, userPrompt, Function.identity());
    }

    public <T> T chatCompletion(String systemPrompt, String userPrompt, Function<String, T> responseHandler) {
        return chatCompletion(LlmCallContext.none(), systemPrompt, userPrompt, responseHandler).result();
    }

    public <T> LlmGatewayResponse<T> chatCompletion(LlmCallContext context,
                                                    String systemPrompt,
                                                    String userPrompt,
                                                    Function<String, T> responseHandler) {
        LlmCallContext safeContext = context == null ? LlmCallContext.none() : context;
        GovernanceTraceService.SpanScope logicalSpan = openLogicalSpan(safeContext, "LLM_CALL");
        List<ModelCandidate> candidates = candidates();
        List<String> failures = new ArrayList<>();
        UsageAccumulator totalUsage = new UsageAccumulator();
        int attemptOrder = 1;
        try {
            for (ModelCandidate candidate : candidates) {
                LlmProviderAdapter adapter = adapter(candidate.providerType());
                int maxRetries = Math.max(candidate.maxRetries(), 0);
                for (int i = 0; i <= maxRetries; i++) {
                    long started = System.nanoTime();
                    LlmProviderResponse response = null;
                    GovernanceTraceService.SpanScope attemptSpan = openAttemptSpan(
                            safeContext, candidate, attemptOrder, i + 1, maxRetries + 1);
                    try {
                        response = adapter.chatCompletion(candidate.runtimeModel(), systemPrompt, userPrompt);
                        totalUsage.add(response.usage());
                        T result = responseHandler.apply(response.content());
                        long durationMs = elapsedMs(started);
                        recordAttempt(candidate, safeContext, attemptOrder, "SUCCESS", null, durationMs,
                                response.usage());
                        attemptSpan.success(usage(response));
                        ModelCallRecordEntity modelCall = recordModelCall(candidate, safeContext, systemPrompt, userPrompt,
                                response, result, durationMs, "SUCCESS", null);
                        logicalSpan.success(totalUsage.total());
                        return new LlmGatewayResponse<>(result, modelCall, candidate.providerCode(),
                                candidate.modelName(), totalUsage.total());
                    } catch (LlmException e) {
                        LlmUsage attemptUsage = response == null ? LlmUsage.empty() : response.usage();
                        failures.add(candidate.modelName() + ": " + e.getMessage());
                        recordAttempt(candidate, safeContext, attemptOrder, "FAILED", e.getMessage(), elapsedMs(started), attemptUsage);
                        attemptSpan.finish("FAILED", attemptUsage, e.getMessage());
                        log.info("LLM model attempt failed, model={}, attempt={}/{}: {}",
                                candidate.modelName(), i + 1, maxRetries + 1, e.getMessage());
                    } catch (RuntimeException e) {
                        LlmUsage attemptUsage = response == null ? LlmUsage.empty() : response.usage();
                        failures.add(candidate.modelName() + ": " + e.getMessage());
                        recordAttempt(candidate, safeContext, attemptOrder, "FAILED", e.getMessage(), elapsedMs(started), attemptUsage);
                        attemptSpan.finish("FAILED", attemptUsage, e.getMessage());
                        log.info("LLM response handling failed, model={}, attempt={}/{}: {}",
                                candidate.modelName(), i + 1, maxRetries + 1, e.getMessage());
                    } finally {
                        attemptSpan.close();
                    }
                    attemptOrder++;
                }
            }
            LlmException exhausted = new LlmException("All configured LLM models failed: " + String.join("; ", failures));
            logicalSpan.fail(exhausted);
            throw exhausted;
        } finally {
            logicalSpan.close();
        }
    }

    public LlmGatewayResponse<LlmAgentProviderResponse> agentCompletion(
            LlmCallContext context,
            String systemPrompt,
            List<LlmAgentMessage> messages,
        List<LlmToolDefinition> tools) {
        LlmCallContext safeContext = context == null ? LlmCallContext.none() : context;
        GovernanceTraceService.SpanScope logicalSpan = openLogicalSpan(safeContext, "LLM_TOOL_CALL");
        List<String> failures = new ArrayList<>();
        int attemptOrder = 1;
        try {
            for (ModelCandidate candidate : candidates()) {
                LlmProviderAdapter adapter = adapter(candidate.providerType());
                if (!adapter.supportsNativeToolCalling()) {
                    failures.add(candidate.modelName() + ": native tool calling unsupported");
                    continue;
                }
                int maxRetries = Math.max(candidate.maxRetries(), 0);
                for (int i = 0; i <= maxRetries; i++) {
                    long started = System.nanoTime();
                    LlmAgentProviderResponse response = null;
                    GovernanceTraceService.SpanScope attemptSpan = openAttemptSpan(
                            safeContext, candidate, attemptOrder, i + 1, maxRetries + 1);
                    try {
                        response = adapter.agentCompletion(candidate.runtimeModel(), systemPrompt, messages, tools);
                        long durationMs = elapsedMs(started);
                        recordAttempt(candidate, safeContext, attemptOrder, "SUCCESS", null, durationMs, response.usage());
                        attemptSpan.success(response.usage());
                        LlmProviderResponse persisted = new LlmProviderResponse(
                                response.content(), response.rawResponse(), response.usage());
                        ModelCallRecordEntity call = recordModelCall(candidate, safeContext, systemPrompt,
                                toJson(messages), persisted, response, durationMs, "SUCCESS", null);
                        logicalSpan.success(response.usage());
                        return new LlmGatewayResponse<>(response, call, candidate.providerCode(), candidate.modelName(), response.usage());
                    } catch (RuntimeException e) {
                        LlmUsage attemptUsage = response == null ? LlmUsage.empty() : response.usage();
                        failures.add(candidate.modelName() + ": " + e.getMessage());
                        recordAttempt(candidate, safeContext, attemptOrder, "FAILED", e.getMessage(), elapsedMs(started), attemptUsage);
                        attemptSpan.finish("FAILED", attemptUsage, e.getMessage());
                        log.info("LLM native tool attempt failed, model={}, attempt={}/{}: {}",
                                candidate.modelName(), i + 1, maxRetries + 1, e.getMessage());
                    } finally {
                        attemptSpan.close();
                    }
                    attemptOrder++;
                }
            }
            LlmException exhausted = new LlmException("All configured LLM models failed for native tool calling: " + String.join("; ", failures));
            logicalSpan.fail(exhausted);
            throw exhausted;
        } finally {
            logicalSpan.close();
        }
    }

    public void testModel(Long modelConfigId) {
        LlmModelConfigEntity model = modelRepository.findById(modelConfigId)
                .orElseThrow(() -> new IllegalArgumentException("Model config not found: " + modelConfigId));
        ModelCandidate candidate = candidate(model);
        adapter(candidate.providerType()).chatCompletion(candidate.runtimeModel(),
                "Return JSON only.",
                "Return {\"ok\":true}.");
    }

    private List<ModelCandidate> candidates() {
        if (modelRepository == null) {
            return List.of(legacyCandidate());
        }
        try {
            List<LlmModelConfigEntity> models =
                    modelRepository.findByEnabledTrueAndProvider_EnabledTrueOrderByPriorityDesc();
            if (models.isEmpty()) {
                return List.of(legacyCandidate());
            }
            return models.stream()
                    .sorted(Comparator.comparingInt(LlmModelConfigEntity::getPriority).reversed())
                    .map(this::candidate)
                    .toList();
        } catch (Exception e) {
            log.warn("Failed to load LLM model chain, falling back to legacy llm.* properties: {}", e.getMessage());
            return List.of(legacyCandidate());
        }
    }

    private ModelCandidate candidate(LlmModelConfigEntity model) {
        String apiKey = resolveApiKey(model.getApiKeyEnv());
        return new ModelCandidate(
                model.getProvider().getProviderCode(),
                model.getProvider().getProviderType(),
                model.getModelName(),
                model.getMaxRetries(),
                model,
                new LlmClient.RuntimeModel(
                        model.getProvider().getBaseUrl(),
                        apiKey,
                        model.getModelName(),
                        model.getTemperature(),
                        Duration.ofSeconds(Math.max(model.getTimeoutSeconds(), 1)),
                        model.getResponseFormat()));
    }

    private ModelCandidate legacyCandidate() {
        return new ModelCandidate(
                "legacy",
                "OPENAI_COMPATIBLE",
                properties.getModel(),
                0,
                null,
                new LlmClient.RuntimeModel(
                        properties.getBaseUrl(),
                        properties.getApiKey(),
                        properties.getModel(),
                        properties.getTemperature(),
                        properties.getTimeout(),
                        "json_object"));
    }

    private LlmProviderAdapter adapter(String providerType) {
        return adapters.stream()
                .filter(candidate -> candidate.supports(providerType))
                .findFirst()
                .orElseThrow(() -> new LlmException("No LLM provider adapter registered for providerType=" + providerType));
    }

    private String resolveApiKey(String apiKeyEnv) {
        if (!StringUtils.hasText(apiKeyEnv)) {
            return "";
        }
        String value = System.getenv(apiKeyEnv.strip());
        return value == null ? "" : value;
    }

    private long elapsedMs(long started) {
        return Math.max(0, (System.nanoTime() - started) / 1_000_000);
    }

    private GovernanceTraceService.SpanScope openLogicalSpan(LlmCallContext context, String type) {
        if (traceService == null || context.governanceRunId() == null) return GovernanceTraceService.SpanScope.noop();
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("operationType", context.operationType());
        attributes.put("promptVersion", context.promptVersion());
        attributes.put("iteration", context.chunkIndex());
        attributes.put("batchIndex", context.chunkIndex());
        attributes.put("relatedTaskIds", context.relatedTaskIds());
        attributes.put("relatedTaskCount", context.relatedTaskIds().size());
        String name = (StringUtils.hasText(context.operationType()) ? context.operationType() : "大模型调用")
                + (context.chunkIndex() == null ? "" : " · 批次 " + context.chunkIndex());
        return traceService.open(context.governanceRunId(), context.governanceGroupId(), type, name,
                "SERIAL", null, context.chunkIndex(), context.chunkIndex(), null, null, attributes);
    }

    private GovernanceTraceService.SpanScope openAttemptSpan(LlmCallContext context,
                                                             ModelCandidate candidate,
                                                             int attemptOrder,
                                                             int retryNumber,
                                                             int maximumAttempts) {
        if (traceService == null || context.governanceRunId() == null) return GovernanceTraceService.SpanScope.noop();
        return traceService.open(context.governanceRunId(), context.governanceGroupId(), "LLM_ATTEMPT",
                candidate.modelName() + " · 尝试 " + retryNumber + "/" + maximumAttempts,
                "SERIAL", null, attemptOrder, context.chunkIndex(), candidate.providerCode(), candidate.modelName(),
                Map.of("attemptOrder", attemptOrder, "retryNumber", retryNumber,
                        "maximumAttempts", maximumAttempts, "fallbackChain", true));
    }

    private void recordAttempt(ModelCandidate candidate,
                               LlmCallContext context,
                               int attemptOrder,
                               String status,
                               String error,
                               long durationMs,
                               LlmUsage usage) {
        if (attemptRepository == null) {
            return;
        }
        try {
            LlmCallAttemptEntity entity = new LlmCallAttemptEntity();
            task(context.taskId()).ifPresent(entity::setTask);
            entity.setModelConfig(candidate.entity());
            entity.setProviderCode(candidate.providerCode());
            entity.setModelName(candidate.modelName());
            entity.setStage(context.stageName());
            entity.setOperationType(context.operationType());
            entity.setRuleCode(context.ruleCode());
            entity.setRuleVersionId(context.ruleVersionId());
            entity.setChunkIndex(context.chunkIndex());
            entity.setPageFrom(context.pageFrom());
            entity.setPageTo(context.pageTo());
            entity.setGovernanceRunId(context.governanceRunId());
            entity.setGovernanceGroupId(context.governanceGroupId());
            entity.setGovernanceProposalId(context.governanceProposalId());
            entity.setPromptVersion(context.promptVersion());
            entity.setAttemptOrder(attemptOrder);
            entity.setCallStatus(status);
            entity.setErrorMessage(error);
            entity.setDurationMs(durationMs);
            entity.setInputTokenCount(usage(usage).inputTokens());
            entity.setOutputTokenCount(usage(usage).outputTokens());
            entity.setCacheHitTokenCount(usage(usage).cacheHitTokens());
            entity.setRawUsageJson(usage(usage).rawUsageJson());
            entity.setCreatedAt(Instant.now());
            attemptRepository.save(entity);
        } catch (Exception e) {
            log.debug("Failed to persist LLM call attempt: {}", e.getMessage());
        }
    }

    private ModelCallRecordEntity recordModelCall(ModelCandidate candidate,
                                                  LlmCallContext context,
                                                  String systemPrompt,
                                                  String userPrompt,
                                                  LlmProviderResponse response,
                                                  Object structuredResult,
                                                  long durationMs,
                                                  String status,
                                                  String error) {
        if (modelCallRepository == null || (context.taskId() == null && context.governanceRunId() == null)) {
            return null;
        }
        ReviewTaskEntity task = task(context.taskId()).orElse(null);
        ModelCallRecordEntity record = new ModelCallRecordEntity();
        record.setTask(task);
        record.setModelConfig(candidate.entity());
        record.setStage(StringUtils.hasText(context.stageName()) ? context.stageName() : "UNKNOWN");
        record.setOperationType(context.operationType());
        record.setRuleCode(context.ruleCode());
        record.setRuleVersionId(context.ruleVersionId());
        record.setChunkIndex(context.chunkIndex());
        record.setPageFrom(context.pageFrom());
        record.setPageTo(context.pageTo());
        record.setGovernanceRunId(context.governanceRunId());
        record.setGovernanceGroupId(context.governanceGroupId());
        record.setGovernanceProposalId(context.governanceProposalId());
        record.setProvider(candidate.providerCode());
        record.setModelName(candidate.modelName());
        record.setPromptVersion(StringUtils.hasText(context.promptVersion())
                ? context.promptVersion()
                : task == null ? null : task.getReviewVersion());
        record.setRuleVersion(task == null ? null : task.getReviewVersion());
        record.setRequestSummary(requestSummary(systemPrompt, userPrompt, context));
        record.setRawResponse(response == null ? null : response.rawResponse());
        record.setStructuredResponse(toJson(structuredResult));
        record.setInputCharCount((systemPrompt == null ? 0 : systemPrompt.length())
                + (userPrompt == null ? 0 : userPrompt.length()));
        record.setInputTokenCount(usage(response).inputTokens());
        record.setOutputTokenCount(usage(response).outputTokens());
        record.setCacheHitTokenCount(usage(response).cacheHitTokens());
        record.setRawUsageJson(usage(response).rawUsageJson());
        record.setDurationMs(durationMs);
        record.setCallStatus(status);
        record.setErrorMessage(error);
        record.setCreatedAt(Instant.now());
        return modelCallRepository.save(record);
    }

    private java.util.Optional<ReviewTaskEntity> task(Long taskId) {
        if (taskRepository == null || taskId == null) {
            return java.util.Optional.empty();
        }
        return taskRepository.findById(taskId);
    }

    private String requestSummary(String systemPrompt, String userPrompt, LlmCallContext context) {
        return "operation=" + nullToEmpty(context.operationType())
                + ", chunk=" + nullToEmpty(context.chunkIndex())
                + ", pages=" + nullToEmpty(context.pageFrom()) + "-" + nullToEmpty(context.pageTo())
                + ", relatedTaskIds=" + context.relatedTaskIds()
                + ", inputChars=" + ((systemPrompt == null ? 0 : systemPrompt.length())
                + (userPrompt == null ? 0 : userPrompt.length()));
    }

    private String nullToEmpty(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private LlmUsage usage(LlmProviderResponse response) {
        return response == null ? LlmUsage.empty() : usage(response.usage());
    }

    private LlmUsage usage(LlmUsage usage) {
        return usage == null ? LlmUsage.empty() : usage;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    private record ModelCandidate(
            String providerCode,
            String providerType,
            String modelName,
            int maxRetries,
            LlmModelConfigEntity entity,
            LlmClient.RuntimeModel runtimeModel
    ) {
    }

    private static final class UsageAccumulator {
        private int input;
        private int output;
        private int cache;
        private boolean hasInput;
        private boolean hasOutput;

        private void add(LlmUsage usage) {
            if (usage == null) return;
            if (usage.inputTokens() != null) {
                input += usage.inputTokens();
                hasInput = true;
            }
            if (usage.outputTokens() != null) {
                output += usage.outputTokens();
                hasOutput = true;
            }
            if (usage.cacheHitTokens() != null) cache += usage.cacheHitTokens();
        }

        private LlmUsage total() {
            return new LlmUsage(hasInput ? input : null, hasOutput ? output : null, cache, null);
        }
    }

    private record LegacyClientAdapter(LlmClient client) implements LlmProviderAdapter {
        @Override
        public boolean supports(String providerType) {
            return true;
        }

        @Override
        public LlmProviderResponse chatCompletion(LlmClient.RuntimeModel runtimeModel,
                                                  String systemPrompt,
                                                  String userPrompt) {
            String content = client.chatCompletion(runtimeModel, systemPrompt, userPrompt);
            return new LlmProviderResponse(content, content, LlmUsage.empty());
        }
    }
}
