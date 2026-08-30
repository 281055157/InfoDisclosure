package com.example.disclosurereview.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 一条问题/发现。
 *
 * @param verified 证据是否已在原文中回查命中（模型证据无效时为 false）
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReviewIssue(
        IssueType issueType,
        Severity severity,
        Double confidence,
        Integer pageNumber,
        String evidenceText,
        String explanation,
        String suggestion,
        String source,
        Boolean verified,
        String ruleCode,
        Long ruleVersionId,
        Long executionId
) {
    public ReviewIssue(IssueType issueType,
                       Severity severity,
                       Double confidence,
                       Integer pageNumber,
                       String evidenceText,
                       String explanation,
                       String suggestion,
                       String source,
                       Boolean verified) {
        this(issueType, severity, confidence, pageNumber, evidenceText,
                explanation, suggestion, source, verified, null, null, null);
    }

    public ReviewIssue withVerified(boolean verified) {
        return new ReviewIssue(issueType, severity, confidence, pageNumber, evidenceText,
                explanation, suggestion, source, verified, ruleCode, ruleVersionId, executionId);
    }

    public ReviewIssue withRuleTrace(String ruleCode, Long ruleVersionId, Long executionId) {
        return new ReviewIssue(issueType, severity, confidence, pageNumber, evidenceText,
                explanation, suggestion, source, verified, ruleCode, ruleVersionId, executionId);
    }
}
