package com.example.disclosurereview.rule.domain;

import com.example.disclosurereview.rule.RuleReviewService;

import java.util.List;

public record PlannedRuleReviewOutcome(
        RuleReviewService.RuleReviewOutcome ruleOutcome,
        List<SemanticRuleCheck> semanticChecks
) {
    public PlannedRuleReviewOutcome {
        semanticChecks = semanticChecks == null ? List.of() : List.copyOf(semanticChecks);
    }
}
