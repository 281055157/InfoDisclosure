package com.example.disclosurereview.governance.dto;

import com.example.disclosurereview.governance.domain.*;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.List;

public final class RuleGovernanceDtos {
    private RuleGovernanceDtos() {}

    public record RunResponse(Long id, String runNo, GovernanceRunTriggerType triggerType, GovernanceRunStatus status,
                              Instant startedAt, Instant finishedAt, int scannedFeedbackCount, int createdGroupCount,
                              int createdProposalCount, int failedGroupCount, int skippedFeedbackCount,
                              String skipReasonSummary, Long modelConfigId,
                              int inputTokens, int outputTokens, int cacheHitTokens, Long durationMs,
                              String errorMessage, Instant createdAt) {}

    public record GroupResponse(Long id, String groupKey, GovernanceIntent governanceIntent, String feedbackType,
                                String ruleCode, String ruleName, Long ruleVersionId,
                                String ruleVersion, String documentCategory, String declaredFileType,
                                String productSeries, String issueType, int feedbackCount, GovernanceGroupStatus status,
                                boolean hasProposal, Instant latestFeedbackAt, Long governanceRunId,
                                String errorMessage, Instant createdAt) {}

    public record FeedbackSampleResponse(Long feedbackId, Long taskId, String taskNo, Long issueId,
                                         String issueType, String issueDescription, Integer evidencePage,
                                         String evidenceText, String ruleCode, Long ruleVersionId,
                                         String documentCategory, String declaredFileType, String productCode,
                                         String falsePositiveReason, JsonNode issueSnapshot,
                                         JsonNode manualSnapshot, String reviewer, Instant createdAt) {}

    public record ProposalSummaryResponse(Long id, String proposalNo, ProposalType proposalType,
                                          ProposalStatus proposalStatus, String ruleCode, String ruleName,
                                          String sourceRuleVersion, RootCauseType rootCauseType,
                                          int feedbackCount, Double agentConfidence, String backtestRisk,
                                          String documentCategory, String declaredFileType,
                                          String agentModel, String reviewedBy, Instant createdAt, Instant reviewedAt,
                                          long actionCount) {}

    public record ProposalDetailResponse(ProposalSummaryResponse summary, GroupResponse group,
                                         String problemSummary, String rootCauseAnalysis, String changeReason,
                                         String expectedEffect, String riskDescription,
                                         JsonNode beforeRule, JsonNode afterRule, JsonNode finalRule,
                                         JsonNode validationResult, JsonNode backtestResult, JsonNode affectedScope,
                                         String optimizationCategory, String optimizationAdvice,
                                         String responsibleModule, String priority, boolean humanFollowUpRequired,
                                         String agentProvider, String agentModel, String promptVersion,
                                         Long draftRuleDefinitionId, Long draftRuleVersionId,
                                         String reviewComment, String rejectionReason, String deferReason,
                                         Instant deferredUntil, List<ProposalActionResponse> actions,
                                         List<FeedbackSampleResponse> feedbacks,
                                         List<MemoryResponse> memories, List<ToolCallResponse> toolCalls,
                                         List<GovernanceAuditResponse> auditTrail) {}

    public record ProposalActionResponse(Long id, int sequenceNo, ProposalType actionType,
                                         String actionStatus, String ruleCode, Long sourceRuleVersionId,
                                         JsonNode beforeRule, JsonNode afterRule, JsonNode validationResult,
                                         JsonNode backtestResult, JsonNode affectedScope,
                                         Long draftRuleDefinitionId, Long draftRuleVersionId) {}

    public record MemoryResponse(Long id, GovernanceMemoryType memoryType, String ruleCode, String ruleVersion,
                                 String documentCategory, String declaredFileType, RootCauseType rootCauseType,
                                 ProposalType proposalType, Long proposalId, GovernanceDecision decision,
                                 String decisionReason, String humanComment, String caseSummary,
                                 String agentSuggestionSummary, String finalChangeSummary,
                                 JsonNode backtestSummary, JsonNode effectSummary, Instant createdAt) {}

    public record ToolCallResponse(Long id, int iteration, String toolName, String status,
                                   String candidateHash, Long durationMs, String errorMessage, Instant createdAt) {}
    public record TraceResponse(Long runId, String runNo, String traceId, GovernanceRunStatus status,
                                String currentStep, String currentMessage, boolean instrumented,
                                List<TraceNodeResponse> nodes, List<TraceEdgeResponse> edges) {}
    public record TraceNodeResponse(String id, String parentId, Long governanceGroupId,
                                    String type, String name, String status, String executionMode,
                                    String parallelGroup, Integer sequence, Integer iteration,
                                    String provider, String model, int inputTokens, int outputTokens,
                                    int cacheHitTokens, Long durationMs, String errorMessage,
                                    JsonNode attributes, Instant startedAt, Instant finishedAt) {}
    public record TraceEdgeResponse(String source, String target, String kind) {}
    public record GovernanceAuditResponse(Long id, String operationType, String operator, String detail,
                                          String beforeValue, String afterValue, Instant createdAt) {}

    public record ApproveRequest(String comment) {}
    public record ApproveWithModificationRequest(@NotNull JsonNode candidateRule, String comment) {}
    public record RejectRequest(@NotNull ProposalRejectionReason reason, String comment) {}
    public record DeferRequest(@NotBlank String reason, Instant reviewAfter) {}
    public record ApplyRequest(String comment) {}
    public record CandidateValidationRequest(@NotNull JsonNode candidateRule, String sourceRuleCode, Boolean creatingRule) {}
    public record BacktestRequest(@NotNull Long feedbackGroupId, @NotNull JsonNode candidateRule,
                                  @Min(1) @Max(1000) Integer maximumSamples) {}
}
