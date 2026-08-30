package com.example.disclosurereview.rule.domain;

public record SemanticRuleCandidate(
        Integer pageNumber,
        String evidenceText,
        String explanation
) {
}
