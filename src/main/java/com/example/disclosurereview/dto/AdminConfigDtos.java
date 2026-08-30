package com.example.disclosurereview.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class AdminConfigDtos {

    private AdminConfigDtos() {
    }

    public record RuleConfigResponse(
            Long id,
            String ruleCode,
            String ruleName,
            String ruleType,
            String ruleCategory,
            boolean enabled,
            String severity,
            Double confidence,
            String documentTypes,
            String productScope,
            String parametersJson,
            String versionCode,
            Long activeVersionId,
            Integer priority,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RuleConfigRequest(
            String ruleCode,
            String ruleName,
            String ruleType,
            String ruleCategory,
            Boolean enabled,
            String severity,
            Double confidence,
            String documentTypes,
            String productScope,
            String parametersJson,
            String versionCode,
            Long activeVersionId,
            Integer priority
    ) {
    }

    public record RuleDetailResponse(
            RuleConfigResponse rule,
            List<RuleVersionResponse> versions
    ) {
    }

    public record RuleVersionResponse(
            Long id,
            String versionCode,
            Integer versionNumber,
            String executorType,
            String status,
            boolean active,
            String description,
            String scopeJson,
            String conditionJson,
            String actionJson,
            String promptJson,
            String changeSummary,
            Instant publishedAt,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RuleVersionRequest(
            String description,
            String executorType,
            String scopeJson,
            String conditionJson,
            String actionJson,
            String promptJson,
            String changeSummary,
            Boolean active
    ) {
    }

    public record RuleValidationResponse(
            boolean valid,
            List<String> errors
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RuleTestRequest(
            String testText,
            String documentCategory,
            String documentType,
            String declaredProductCode,
            String declaredDocumentType,
            String b9Value
    ) {
    }

    public record RuleTestResponse(
            String status,
            boolean matched,
            int issueCount,
            List<?> productCodeCandidates,
            List<?> productNameCandidates,
            List<?> issues,
            String detail
    ) {
    }

    public record RuleExecutionResponse(
            Long id,
            Long taskId,
            String taskNo,
            String originalFileName,
            String taskStatus,
            Instant taskCreatedAt,
            String ruleCode,
            String ruleVersion,
            Long ruleVersionId,
            String executionStatus,
            boolean matched,
            int issueCount,
            Long durationMs,
            String errorMessage,
            String resultDetail,
            Instant createdAt
    ) {
    }

    public record ExecutorSchemaResponse(
            Map<String, Object> schemas
    ) {
    }

    public record ModelConfigResponse(
            Long id,
            String providerCode,
            String providerType,
            String baseUrl,
            String modelCode,
            String modelName,
            int priority,
            boolean enabled,
            int timeoutSeconds,
            int maxRetries,
            double temperature,
            String responseFormat,
            String apiKeyEnv,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record ProviderConfigResponse(
            Long id,
            String providerCode,
            String providerType,
            String baseUrl,
            boolean enabled,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProviderConfigRequest(
            String providerCode,
            String providerType,
            String baseUrl,
            Boolean enabled
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ModelConfigRequest(
            String providerCode,
            String providerType,
            String baseUrl,
            String modelCode,
            String modelName,
            Integer priority,
            Boolean enabled,
            Integer timeoutSeconds,
            Integer maxRetries,
            Double temperature,
            String responseFormat,
            String apiKeyEnv
    ) {
    }

    public record ModelTestResponse(
            boolean ok,
            String message
    ) {
    }

    public record RuleFeedbackResponse(
            Long id,
            Long taskId,
            Long issueId,
            String ruleCode,
            Long ruleVersionId,
            Long ruleExecutionId,
            String feedbackType,
            String documentCategory,
            String declaredProductCode,
            String declaredDocumentType,
            String feedbackSource,
            String feedbackTags,
            String aggregationKey,
            String processStatus,
            String issueSnapshotJson,
            String manualSnapshotJson,
            String comment,
            String reviewer,
            Instant createdAt,
            Instant processedAt
    ) {
    }

    public record DeleteTaskResponse(
            Long taskId,
            String taskNo,
            String originalFileName,
            boolean databaseDeleted,
            boolean fileDeleted,
            boolean parameterFileDeleted,
            List<String> warnings
    ) {
    }

    public record AdminPageResponse<T>(
            List<T> content
    ) {
    }
}
