package com.example.disclosurereview.governance.persistence.entity;

import com.example.disclosurereview.governance.domain.GovernanceRunStatus;
import com.example.disclosurereview.governance.domain.GovernanceRunTriggerType;
import com.example.disclosurereview.persistence.entity.LlmModelConfigEntity;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "rule_governance_run")
public class RuleGovernanceRunEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "run_no", nullable = false, unique = true, length = 64) private String runNo;
    @Column(name = "trace_id", nullable = false, unique = true, length = 64) private String traceId;
    @Enumerated(EnumType.STRING) @Column(name = "trigger_type", nullable = false, length = 32) private GovernanceRunTriggerType triggerType;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false, length = 32) private GovernanceRunStatus status;
    @Column(name = "started_at") private Instant startedAt;
    @Column(name = "finished_at") private Instant finishedAt;
    @Column(name = "scanned_feedback_count", nullable = false) private int scannedFeedbackCount;
    @Column(name = "created_group_count", nullable = false) private int createdGroupCount;
    @Column(name = "created_proposal_count", nullable = false) private int createdProposalCount;
    @Column(name = "failed_group_count", nullable = false) private int failedGroupCount;
    @Column(name = "skipped_feedback_count", nullable = false) private int skippedFeedbackCount;
    @Column(name = "skip_reason_summary", columnDefinition = "text") private String skipReasonSummary;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "model_config_id") private LlmModelConfigEntity modelConfig;
    @Column(name = "input_token_count", nullable = false) private int inputTokenCount;
    @Column(name = "output_token_count", nullable = false) private int outputTokenCount;
    @Column(name = "cache_hit_token_count", nullable = false) private int cacheHitTokenCount;
    @Column(name = "duration_ms") private Long durationMs;
    @Column(name = "error_message", columnDefinition = "text") private String errorMessage;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private long version;

    @PrePersist
    void initializeTraceId() {
        if (traceId == null || traceId.isBlank()) traceId = "governance-" + UUID.randomUUID();
    }

    public Long getId() { return id; }
    public String getRunNo() { return runNo; }
    public void setRunNo(String v) { runNo = v; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String v) { traceId = v; }
    public GovernanceRunTriggerType getTriggerType() { return triggerType; }
    public void setTriggerType(GovernanceRunTriggerType v) { triggerType = v; }
    public GovernanceRunStatus getStatus() { return status; }
    public void setStatus(GovernanceRunStatus v) { status = v; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant v) { startedAt = v; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant v) { finishedAt = v; }
    public int getScannedFeedbackCount() { return scannedFeedbackCount; }
    public void setScannedFeedbackCount(int v) { scannedFeedbackCount = v; }
    public int getCreatedGroupCount() { return createdGroupCount; }
    public void setCreatedGroupCount(int v) { createdGroupCount = v; }
    public int getCreatedProposalCount() { return createdProposalCount; }
    public void setCreatedProposalCount(int v) { createdProposalCount = v; }
    public int getFailedGroupCount() { return failedGroupCount; }
    public void setFailedGroupCount(int v) { failedGroupCount = v; }
    public int getSkippedFeedbackCount() { return skippedFeedbackCount; }
    public void setSkippedFeedbackCount(int v) { skippedFeedbackCount = v; }
    public String getSkipReasonSummary() { return skipReasonSummary; }
    public void setSkipReasonSummary(String v) { skipReasonSummary = v; }
    public LlmModelConfigEntity getModelConfig() { return modelConfig; }
    public void setModelConfig(LlmModelConfigEntity v) { modelConfig = v; }
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
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant v) { createdAt = v; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant v) { updatedAt = v; }
    public long getVersion() { return version; }
}
