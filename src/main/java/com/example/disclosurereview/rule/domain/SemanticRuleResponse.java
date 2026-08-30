package com.example.disclosurereview.rule.domain;

public record SemanticRuleResponse(
        String ruleCode,
        boolean violated,
        Double confidence,
        Integer pageNumber,
        String evidenceText,
        String explanation,
        String suggestion
) {
}
