package com.example.disclosurereview.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "model_call_record")
public class ModelCallRecordEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id")
    private ReviewTaskEntity task;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "model_config_id")
    private LlmModelConfigEntity modelConfig;

    @Column(name = "stage", nullable = false, length = 64)
    private String stage;

    @Column(name = "operation_type", length = 128)
    private String operationType;

    @Column(name = "rule_code", length = 128)
    private String ruleCode;

    @Column(name = "rule_version_id")
    private Long ruleVersionId;

    @Column(name = "chunk_index")
    private Integer chunkIndex;

    @Column(name = "page_from")
    private Integer pageFrom;

    @Column(name = "page_to")
    private Integer pageTo;

    @Column(name = "governance_run_id")
    private Long governanceRunId;

    @Column(name = "governance_group_id")
    private Long governanceGroupId;

    @Column(name = "governance_proposal_id")
    private Long governanceProposalId;

    @Column(name = "provider", length = 128)
    private String provider;

    @Column(name = "model_name", length = 256)
    private String modelName;

    @Column(name = "prompt_version", length = 64)
    private String promptVersion;

    @Column(name = "rule_version", length = 64)
    private String ruleVersion;

    @Column(name = "request_summary", columnDefinition = "text")
    private String requestSummary;

    @Column(name = "raw_response", columnDefinition = "text")
    private String rawResponse;

    @Column(name = "structured_response", columnDefinition = "text")
    private String structuredResponse;

    @Column(name = "input_char_count")
    private Integer inputCharCount;

    @Column(name = "input_token_count")
    private Integer inputTokenCount;

    @Column(name = "output_token_count")
    private Integer outputTokenCount;

    @Column(name = "cache_hit_token_count")
    private Integer cacheHitTokenCount;

    @Column(name = "raw_usage_json", columnDefinition = "text")
    private String rawUsageJson;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "call_status", nullable = false, length = 64)
    private String callStatus;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public ReviewTaskEntity getTask() {
        return task;
    }

    public void setTask(ReviewTaskEntity task) {
        this.task = task;
    }

    public LlmModelConfigEntity getModelConfig() {
        return modelConfig;
    }

    public void setModelConfig(LlmModelConfigEntity modelConfig) {
        this.modelConfig = modelConfig;
    }

    public String getStage() {
        return stage;
    }

    public void setStage(String stage) {
        this.stage = stage;
    }

    public String getOperationType() {
        return operationType;
    }

    public void setOperationType(String operationType) {
        this.operationType = operationType;
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public void setRuleCode(String ruleCode) {
        this.ruleCode = ruleCode;
    }

    public Long getRuleVersionId() {
        return ruleVersionId;
    }

    public void setRuleVersionId(Long ruleVersionId) {
        this.ruleVersionId = ruleVersionId;
    }

    public Integer getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(Integer chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public Integer getPageFrom() {
        return pageFrom;
    }

    public void setPageFrom(Integer pageFrom) {
        this.pageFrom = pageFrom;
    }

    public Integer getPageTo() {
        return pageTo;
    }

    public void setPageTo(Integer pageTo) {
        this.pageTo = pageTo;
    }

    public Long getGovernanceRunId() { return governanceRunId; }
    public void setGovernanceRunId(Long value) { governanceRunId = value; }
    public Long getGovernanceGroupId() { return governanceGroupId; }
    public void setGovernanceGroupId(Long value) { governanceGroupId = value; }
    public Long getGovernanceProposalId() { return governanceProposalId; }
    public void setGovernanceProposalId(Long value) { governanceProposalId = value; }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public void setPromptVersion(String promptVersion) {
        this.promptVersion = promptVersion;
    }

    public String getRuleVersion() {
        return ruleVersion;
    }

    public void setRuleVersion(String ruleVersion) {
        this.ruleVersion = ruleVersion;
    }

    public String getRequestSummary() {
        return requestSummary;
    }

    public void setRequestSummary(String requestSummary) {
        this.requestSummary = requestSummary;
    }

    public String getRawResponse() {
        return rawResponse;
    }

    public void setRawResponse(String rawResponse) {
        this.rawResponse = rawResponse;
    }

    public String getStructuredResponse() {
        return structuredResponse;
    }

    public void setStructuredResponse(String structuredResponse) {
        this.structuredResponse = structuredResponse;
    }

    public Integer getInputCharCount() {
        return inputCharCount;
    }

    public void setInputCharCount(Integer inputCharCount) {
        this.inputCharCount = inputCharCount;
    }

    public Integer getInputTokenCount() {
        return inputTokenCount;
    }

    public void setInputTokenCount(Integer inputTokenCount) {
        this.inputTokenCount = inputTokenCount;
    }

    public Integer getOutputTokenCount() {
        return outputTokenCount;
    }

    public void setOutputTokenCount(Integer outputTokenCount) {
        this.outputTokenCount = outputTokenCount;
    }

    public Integer getCacheHitTokenCount() {
        return cacheHitTokenCount;
    }

    public void setCacheHitTokenCount(Integer cacheHitTokenCount) {
        this.cacheHitTokenCount = cacheHitTokenCount;
    }

    public String getRawUsageJson() {
        return rawUsageJson;
    }

    public void setRawUsageJson(String rawUsageJson) {
        this.rawUsageJson = rawUsageJson;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public String getCallStatus() {
        return callStatus;
    }

    public void setCallStatus(String callStatus) {
        this.callStatus = callStatus;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
