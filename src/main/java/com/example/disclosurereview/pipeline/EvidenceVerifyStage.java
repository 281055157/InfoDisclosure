package com.example.disclosurereview.pipeline;

import com.example.disclosurereview.llm.EvidenceVerifier;
import com.example.disclosurereview.model.DocumentPage;
import com.example.disclosurereview.model.ReviewIssue;
import com.example.disclosurereview.model.ReviewResult;
import com.example.disclosurereview.model.ReviewStage;
import com.example.disclosurereview.model.ReviewTaskStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class EvidenceVerifyStage implements ReviewStageHandler {

    private final ReviewStageSupport support;
    private final ReviewTaskContextStore contextStore;
    private final EvidenceVerifier evidenceVerifier;
    private final ObjectMapper objectMapper;

    public EvidenceVerifyStage(ReviewStageSupport support,
                               ReviewTaskContextStore contextStore,
                               EvidenceVerifier evidenceVerifier,
                               ObjectMapper objectMapper) {
        this.support = support;
        this.contextStore = contextStore;
        this.evidenceVerifier = evidenceVerifier;
        this.objectMapper = objectMapper;
    }

    @Override
    public ReviewStage stage() {
        return ReviewStage.EVIDENCE_VERIFYING;
    }

    @Override
    public int order() {
        return 60;
    }

    @Override
    public StageResult handle(ReviewPipelineContext context) {
        Long taskId = context.getTaskId();
        support.transition(taskId, ReviewTaskStatus.EVIDENCE_VERIFYING,
                context.isRetry() ? "Retry evidence verification stage" : "Start evidence verification");
        support.updateStage(taskId, ReviewStage.EVIDENCE_VERIFYING);
        ObjectNode root = contextStore.load(taskId);
        ReviewResult draft = objectMapper.convertValue(root.path("reviewDraft"), ReviewResult.class);
        List<DocumentPage> pages = support.persistedPages(taskId);
        ReviewResult verified = verifyDraft(draft, pages);
        root.set("reviewDraft", objectMapper.valueToTree(verified));
        root.putObject("evidenceVerification")
                .put("ruleIssueCount", verified.ruleResult() == null ? 0 : verified.ruleResult().issues().size())
                .put("llmIssueCount", verified.llmResult() == null ? 0 : verified.llmResult().issues().size());
        contextStore.save(taskId, root);
        return StageResult.completed(stage(), "Evidence verified");
    }

    private ReviewResult verifyDraft(ReviewResult draft, List<DocumentPage> pages) {
        if (draft == null) {
            throw new IllegalStateException("审核草稿不存在，无法执行证据回查");
        }
        ReviewResult.RuleResultInfo ruleResult = draft.ruleResult();
        if (ruleResult != null) {
            ruleResult = new ReviewResult.RuleResultInfo(
                    ruleResult.productCodeCandidates(),
                    ruleResult.productNameCandidates(),
                    ruleResult.placeholders(),
                    verifyIssues(ruleResult.issues(), pages));
        }
        ReviewResult.LlmResultInfo llmResult = draft.llmResult();
        if (llmResult != null) {
            llmResult = new ReviewResult.LlmResultInfo(
                    llmResult.mainProductCode(),
                    llmResult.mainProductName(),
                    llmResult.candidateDocumentType(),
                    llmResult.otherProductReferences(),
                    llmResult.documentScope(),
                    llmResult.targetProductAssessment(),
                    llmResult.targetProductRows(),
                    llmResult.productOccurrences(),
                    llmResult.agencyAssessment(),
                    verifyIssues(llmResult.issues(), pages),
                    llmResult.summary(),
                    llmResult.manualReviewSuggestion());
        }
        return new ReviewResult(
                draft.taskId(),
                draft.technicalStatus(),
                draft.businessRisk(),
                draft.fileInfo(),
                draft.declaredInfo(),
                draft.productMaster(),
                ruleResult,
                llmResult,
                draft.documentScope(),
                draft.candidateDocumentType(),
                draft.targetProductAssessment(),
                draft.targetProductRows(),
                draft.productOccurrences(),
                draft.agencyAssessment(),
                draft.mergedIssues(),
                draft.statusDetail(),
                draft.createdAt(),
                draft.completedAt());
    }

    private List<ReviewIssue> verifyIssues(List<ReviewIssue> issues, List<DocumentPage> pages) {
        if (issues == null || issues.isEmpty()) {
            return List.of();
        }
        return issues.stream()
                .map(issue -> {
                    if (Boolean.TRUE.equals(issue.verified()) && issue.pageNumber() == null) {
                        return issue;
                    }
                    return evidenceVerifier.verifyIssue(issue, pages);
                })
                .filter(issue -> Boolean.TRUE.equals(issue.verified()))
                .toList();
    }
}
