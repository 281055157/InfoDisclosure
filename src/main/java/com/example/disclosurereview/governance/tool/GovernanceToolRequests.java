package com.example.disclosurereview.governance.tool;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;

public final class GovernanceToolRequests {
    private GovernanceToolRequests() {}

    public record GroupRequest(@NotNull Long groupId) {}
    public record FeedbackSamplesRequest(@NotNull Long groupId, @Min(1) @Max(100) Integer limit) {}
    public record RuleDefinitionRequest(@NotBlank String ruleCode) {}
    public record RuleVersionRequest(@NotBlank String ruleCode, @NotNull Integer version) {}
    public record RuleExecutionRequest(@NotBlank String ruleCode, @NotNull Integer version, @NotEmpty List<Long> taskIds) {}
    public record DocumentContextRequest(@NotNull Long taskId, @NotNull @Min(1) Integer pageNumber, Boolean includeAdjacentPages) {}
    public record MemoryRequest(String ruleCode, String documentCategory, String declaredFileType,
                                String rootCauseType, @Min(1) @Max(50) Integer limit) {}
    public record CandidateRequest(@NotNull JsonNode candidateRule, String sourceRuleCode, Boolean creatingRule) {}
    public record RegexRequest(@NotEmpty List<@NotBlank String> patterns, String candidateHash) {}
    public record BacktestRequest(@NotNull Long feedbackGroupId, @NotNull JsonNode candidateRule,
                                  @Min(1) @Max(1000) Integer maximumSamples) {}
    public record CompareRequest(@NotNull Long sourceRuleVersionId, @NotNull JsonNode candidateRule) {}
    public record EstimateRequest(@NotNull Long feedbackGroupId, @NotNull JsonNode candidateRule) {}
    public record ProposalRequest(
            @NotNull Long governanceGroupId,
            @NotBlank String rootCauseType,
            @NotBlank String problemSummary,
            @NotBlank String rootCauseAnalysis,
            String changeReason,
            JsonNode candidateRule,
            String expectedEffect,
            String riskDescription,
            @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double agentConfidence,
            String optimizationCategory,
            String optimizationAdvice,
            String responsibleModule,
            String priority,
            Boolean humanFollowUpRequired
    ) {}
    public record ProposalActionRequest(
            @NotBlank String actionType,
            String ruleCode,
            Long sourceRuleVersionId,
            @NotNull JsonNode candidateRule
    ) {}
    public record CompositeProposalRequest(
            @NotNull Long governanceGroupId,
            @NotBlank String rootCauseType,
            @NotBlank String problemSummary,
            @NotBlank String rootCauseAnalysis,
            @NotBlank String changeReason,
            @NotBlank String expectedEffect,
            @NotBlank String riskDescription,
            @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double agentConfidence,
            @NotEmpty List<@Valid ProposalActionRequest> actions,
            String responsibleModule,
            String priority,
            Boolean humanFollowUpRequired
    ) {}
}
