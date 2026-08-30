package com.example.disclosurereview.strategy;

import com.example.disclosurereview.model.DocumentType;
import com.example.disclosurereview.model.LlmReviewResult;
import com.example.disclosurereview.model.TargetProductAssessment;
import com.example.disclosurereview.rule.RuleReviewService;

/** 文件类型感知型目标产品审核策略。 */
public interface DocumentReviewStrategy {

    boolean supports(DocumentType documentType);

    StrategyReviewPolicy buildPolicy(ReviewContext context);

    TargetProductAssessment evaluate(
            ReviewContext context,
            RuleReviewService.RuleReviewOutcome ruleResult,
            LlmReviewResult llmResult
    );
}
