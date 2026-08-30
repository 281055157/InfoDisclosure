package com.example.disclosurereview.rule.domain;

import com.example.disclosurereview.model.IssueType;
import com.example.disclosurereview.model.Severity;

public record RuleAction(
        IssueType issueType,
        Severity severity,
        Double confidence,
        String explanationTemplate,
        String suggestionTemplate,
        String source
) {
    public static RuleAction defaultAction() {
        return new RuleAction(IssueType.UNKNOWN_ISSUE, Severity.UNKNOWN, 0.5,
                "规则命中。", "请人工确认。", "RULE");
    }
}
