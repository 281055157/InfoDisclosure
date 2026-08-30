package com.example.disclosurereview.pipeline;

import com.example.disclosurereview.model.BusinessRisk;
import com.example.disclosurereview.model.BusinessAcceptanceDecision;
import com.example.disclosurereview.model.ReviewIssue;
import com.example.disclosurereview.model.ReviewResult;
import com.example.disclosurereview.model.ReviewStage;
import com.example.disclosurereview.model.ReviewTaskStatus;
import com.example.disclosurereview.model.TargetProductAssessment;
import com.example.disclosurereview.model.TechnicalStatus;
import com.example.disclosurereview.service.ReviewResultMerger;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ResultMergeStage implements ReviewStageHandler {

    private final ReviewStageSupport support;
    private final ReviewTaskContextStore contextStore;
    private final ReviewResultMerger merger;
    private final ObjectMapper objectMapper;

    public ResultMergeStage(ReviewStageSupport support,
                            ReviewTaskContextStore contextStore,
                            ReviewResultMerger merger,
                            ObjectMapper objectMapper) {
        this.support = support;
        this.contextStore = contextStore;
        this.merger = merger;
        this.objectMapper = objectMapper;
    }

    @Override
    public ReviewStage stage() {
        return ReviewStage.RESULT_MERGING;
    }

    @Override
    public int order() {
        return 70;
    }

    @Override
    public StageResult handle(ReviewPipelineContext context) {
        Long taskId = context.getTaskId();
        support.transition(taskId, ReviewTaskStatus.RESULT_MERGING,
                context.isRetry() ? "Retry result merge stage" : "Start result merge");
        support.updateStage(taskId, ReviewStage.RESULT_MERGING);
        ObjectNode root = contextStore.load(taskId);
        ReviewResult draft = objectMapper.convertValue(root.path("reviewDraft"), ReviewResult.class);
        if (draft == null) {
            throw new IllegalStateException("审核草稿不存在，无法合并结果");
        }
        BusinessRisk risk = merger.mergeRisk(draft);
        List<ReviewIssue> mergedIssues = merger.mergeIssues(draft);
        BusinessAcceptanceDecision businessDecision = merger.mergeBusinessAcceptance(draft, risk);
        TargetProductAssessment targetAssessment = withBusinessDecision(
                draft.targetProductAssessment(), businessDecision);
        ReviewResult result = new ReviewResult(
                draft.taskId(),
                draft.technicalStatus(),
                risk,
                draft.fileInfo(),
                draft.declaredInfo(),
                draft.productMaster(),
                draft.ruleResult(),
                draft.llmResult(),
                draft.documentScope(),
                draft.candidateDocumentType(),
                targetAssessment,
                draft.targetProductRows(),
                draft.productOccurrences(),
                draft.agencyAssessment(),
                mergedIssues,
                draft.statusDetail(),
                draft.createdAt(),
                java.time.Instant.now());
        Long modelCallId = root.path("llmReview").path("modelCallId").isNumber()
                ? root.path("llmReview").path("modelCallId").asLong()
                : null;
        support.persistFinalResult(taskId, result, modelCallId);
        root.set("resultMerge", objectMapper.valueToTree(result));
        contextStore.save(taskId, root);

        if (result.technicalStatus() == TechnicalStatus.LLM_FAILED
                || result.technicalStatus() == TechnicalStatus.LLM_CALL_FAILED
                || result.technicalStatus() == TechnicalStatus.LLM_TIMEOUT
                || result.technicalStatus() == TechnicalStatus.LLM_RESPONSE_INVALID) {
            support.transition(taskId, ReviewTaskStatus.PARTIAL_SUCCESS,
                    "LLM stage failed, rule result retained for manual review");
            support.updateStage(taskId, ReviewStage.WAITING_MANUAL_REVIEW);
            support.countPartialSuccess();
            return StageResult.terminal(stage(), "Partial success");
        }
        support.transition(taskId, ReviewTaskStatus.WAITING_MANUAL_REVIEW,
                context.isRetry() ? "Retry completed, waiting for manual review" : "Automatic review completed");
        support.updateStage(taskId, ReviewStage.WAITING_MANUAL_REVIEW);
        support.countCompleted();
        return StageResult.terminal(stage(), "Automatic review completed");
    }

    private TargetProductAssessment withBusinessDecision(TargetProductAssessment assessment,
                                                         BusinessAcceptanceDecision decision) {
        if (assessment == null || decision == null) {
            return assessment;
        }
        if (assessment.businessAcceptanceDecision() == decision) {
            return assessment;
        }
        return new TargetProductAssessment(
                assessment.decision(),
                assessment.productIdentityDecision(),
                decision,
                assessment.documentScope(),
                assessment.matchBases(),
                assessment.declaredProductCode(),
                assessment.matchedProductCode(),
                assessment.matchedProductName(),
                assessment.matchedProductSeries(),
                assessment.matchedInstitution(),
                assessment.evidence(),
                assessment.confidence(),
                assessment.explanation(),
                assessment.manualReviewSuggestion());
    }
}
