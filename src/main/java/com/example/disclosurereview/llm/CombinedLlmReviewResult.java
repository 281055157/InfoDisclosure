package com.example.disclosurereview.llm;

import com.example.disclosurereview.model.LlmReviewResult;
import com.example.disclosurereview.rule.domain.SemanticRuleResponse;

import java.util.List;

public record CombinedLlmReviewResult(
        LlmReviewResult reviewResult,
        List<SemanticRuleResponse> semanticRuleResults
) {
    public CombinedLlmReviewResult {
        semanticRuleResults = semanticRuleResults == null ? List.of() : List.copyOf(semanticRuleResults);
    }
}
