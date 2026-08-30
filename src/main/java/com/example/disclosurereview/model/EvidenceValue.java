package com.example.disclosurereview.model;

/**
 * 规则抽取出的候选值，带原文证据。
 *
 * @param source 来源，例如 RULE_LABEL / RULE_MASTER_DATA / RULE_PLACEHOLDER
 */
public record EvidenceValue(
        String value,
        Integer pageNumber,
        String evidenceText,
        String source
) {
}
