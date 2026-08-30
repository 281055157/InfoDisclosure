package com.example.disclosurereview.rule.domain;

import java.util.List;

public record SemanticRuleCheck(
        Long ruleId,
        String ruleCode,
        Long ruleVersionId,
        String ruleVersion,
        Long executionId,
        String executorType,
        String reviewGoal,
        String criteria,
        String responseFormat,
        double minConfidence,
        RuleAction action,
        List<SemanticRuleCandidate> candidates
) {
    public SemanticRuleCheck {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }
}
