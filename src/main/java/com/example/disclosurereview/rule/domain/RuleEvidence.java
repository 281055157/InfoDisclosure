package com.example.disclosurereview.rule.domain;

public record RuleEvidence(
        Integer pageNumber,
        String text,
        String source,
        Boolean verified
) {
}
