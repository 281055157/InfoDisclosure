package com.example.disclosurereview.llm;

import com.example.disclosurereview.model.ReviewStage;

import java.util.List;

public record LlmCallContext(
        Long taskId,
        ReviewStage stage,
        String operationType,
        String ruleCode,
        Long ruleVersionId,
        Integer chunkIndex,
        Integer pageFrom,
        Integer pageTo,
        Long governanceRunId,
        Long governanceGroupId,
        Long governanceProposalId,
        String externalStage,
        String promptVersion,
        List<Long> relatedTaskIds
) {
    public LlmCallContext {
        relatedTaskIds = relatedTaskIds == null ? List.of() : List.copyOf(relatedTaskIds);
    }

    public LlmCallContext(Long taskId,
                          ReviewStage stage,
                          String operationType,
                          String ruleCode,
                          Long ruleVersionId,
                          Integer chunkIndex,
                          Integer pageFrom,
                          Integer pageTo,
                          Long governanceRunId,
                          Long governanceGroupId,
                          Long governanceProposalId,
                          String externalStage,
                          String promptVersion) {
        this(taskId, stage, operationType, ruleCode, ruleVersionId, chunkIndex, pageFrom, pageTo,
                governanceRunId, governanceGroupId, governanceProposalId, externalStage, promptVersion, List.of());
    }

    public LlmCallContext(Long taskId,
                          ReviewStage stage,
                          String operationType,
                          String ruleCode,
                          Long ruleVersionId,
                          Integer chunkIndex,
                          Integer pageFrom,
                          Integer pageTo) {
        this(taskId, stage, operationType, ruleCode, ruleVersionId, chunkIndex, pageFrom, pageTo,
                null, null, null, null, null, List.of());
    }

    public static LlmCallContext none() {
        return new LlmCallContext(null, null, null, null, null, null, null, null,
                null, null, null, null, null, List.of());
    }

    public static LlmCallContext governance(Long runId,
                                            Long groupId,
                                            Long proposalId,
                                            String operationType,
                                            String promptVersion) {
        return new LlmCallContext(null, null, operationType, null, null, null, null, null,
                runId, groupId, proposalId, "FEEDBACK_GOVERNANCE", promptVersion, List.of());
    }

    public static LlmCallContext governanceBacktest(Long runId,
                                                    Long groupId,
                                                    String ruleCode,
                                                    int batchIndex,
                                                    List<Long> relatedTaskIds,
                                                    String promptVersion) {
        return new LlmCallContext(null, null, "FEEDBACK_GOVERNANCE_LLM_BACKTEST", ruleCode,
                null, batchIndex, null, null, runId, groupId, null,
                "FEEDBACK_GOVERNANCE", promptVersion, relatedTaskIds);
    }

    public LlmCallContext withChunk(Integer chunkIndex, Integer pageFrom, Integer pageTo) {
        return new LlmCallContext(taskId, stage, operationType, ruleCode, ruleVersionId,
                chunkIndex, pageFrom, pageTo, governanceRunId, governanceGroupId,
                governanceProposalId, externalStage, promptVersion, relatedTaskIds);
    }

    public String stageName() {
        return stage == null ? externalStage : stage.name();
    }
}
