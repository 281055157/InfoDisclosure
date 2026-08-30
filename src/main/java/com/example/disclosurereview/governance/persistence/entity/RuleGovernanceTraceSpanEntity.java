package com.example.disclosurereview.governance.persistence.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "rule_governance_trace_span")
public class RuleGovernanceTraceSpanEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "trace_id", nullable = false, length = 64) private String traceId;
    @Column(name = "span_id", nullable = false, unique = true, length = 96) private String spanId;
    @Column(name = "parent_span_id", length = 96) private String parentSpanId;
    @Column(name = "governance_run_id", nullable = false) private Long governanceRunId;
    @Column(name = "governance_group_id") private Long governanceGroupId;
    @Column(name = "span_type", nullable = false, length = 32) private String spanType;
    @Column(name = "span_name", nullable = false, length = 160) private String spanName;
    @Column(name = "execution_mode", nullable = false, length = 16) private String executionMode;
    @Column(name = "parallel_group", length = 96) private String parallelGroup;
    @Column(name = "sequence_no") private Integer sequenceNo;
    @Column(name = "iteration_number") private Integer iterationNumber;
    @Column(name = "span_status", nullable = false, length = 32) private String spanStatus;
    @Column(name = "provider_code", length = 128) private String providerCode;
    @Column(name = "model_name", length = 256) private String modelName;
    @Column(name = "input_token_count", nullable = false) private int inputTokenCount;
    @Column(name = "output_token_count", nullable = false) private int outputTokenCount;
    @Column(name = "cache_hit_token_count", nullable = false) private int cacheHitTokenCount;
    @Column(name = "duration_ms") private Long durationMs;
    @Column(name = "error_message", columnDefinition = "text") private String errorMessage;
    @Column(name = "attributes_json", columnDefinition = "text") private String attributesJson;
    @Column(name = "started_at", nullable = false) private Instant startedAt;
    @Column(name = "finished_at") private Instant finishedAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    public Long getId() { return id; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String v) { traceId = v; }
    public String getSpanId() { return spanId; }
    public void setSpanId(String v) { spanId = v; }
    public String getParentSpanId() { return parentSpanId; }
    public void setParentSpanId(String v) { parentSpanId = v; }
    public Long getGovernanceRunId() { return governanceRunId; }
    public void setGovernanceRunId(Long v) { governanceRunId = v; }
    public Long getGovernanceGroupId() { return governanceGroupId; }
    public void setGovernanceGroupId(Long v) { governanceGroupId = v; }
    public String getSpanType() { return spanType; }
    public void setSpanType(String v) { spanType = v; }
    public String getSpanName() { return spanName; }
    public void setSpanName(String v) { spanName = v; }
    public String getExecutionMode() { return executionMode; }
    public void setExecutionMode(String v) { executionMode = v; }
    public String getParallelGroup() { return parallelGroup; }
    public void setParallelGroup(String v) { parallelGroup = v; }
    public Integer getSequenceNo() { return sequenceNo; }
    public void setSequenceNo(Integer v) { sequenceNo = v; }
    public Integer getIterationNumber() { return iterationNumber; }
    public void setIterationNumber(Integer v) { iterationNumber = v; }
    public String getSpanStatus() { return spanStatus; }
    public void setSpanStatus(String v) { spanStatus = v; }
    public String getProviderCode() { return providerCode; }
    public void setProviderCode(String v) { providerCode = v; }
    public String getModelName() { return modelName; }
    public void setModelName(String v) { modelName = v; }
    public int getInputTokenCount() { return inputTokenCount; }
    public void setInputTokenCount(int v) { inputTokenCount = v; }
    public int getOutputTokenCount() { return outputTokenCount; }
    public void setOutputTokenCount(int v) { outputTokenCount = v; }
    public int getCacheHitTokenCount() { return cacheHitTokenCount; }
    public void setCacheHitTokenCount(int v) { cacheHitTokenCount = v; }
    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long v) { durationMs = v; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String v) { errorMessage = v; }
    public String getAttributesJson() { return attributesJson; }
    public void setAttributesJson(String v) { attributesJson = v; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant v) { startedAt = v; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant v) { finishedAt = v; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant v) { createdAt = v; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant v) { updatedAt = v; }
}
