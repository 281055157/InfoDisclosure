package com.example.disclosurereview.rule.domain;

import com.example.disclosurereview.model.EvidenceValue;
import com.example.disclosurereview.model.ReviewIssue;

import java.util.List;

public record RuleExecutionResult(
        RuleExecutionStatus status,
        boolean matched,
        List<EvidenceValue> productCodeCandidates,
        List<EvidenceValue> productNameCandidates,
        List<ReviewIssue> issues,
        List<RuleEvidence> evidence,
        String detail
) {
    public RuleExecutionResult {
        status = status == null ? RuleExecutionStatus.NOT_HIT : status;
        productCodeCandidates = productCodeCandidates == null ? List.of() : List.copyOf(productCodeCandidates);
        productNameCandidates = productNameCandidates == null ? List.of() : List.copyOf(productNameCandidates);
        issues = issues == null ? List.of() : List.copyOf(issues);
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }

    public static RuleExecutionResult notHit() {
        return notHit(null);
    }

    public static RuleExecutionResult notHit(String detail) {
        return new RuleExecutionResult(RuleExecutionStatus.NOT_HIT, false, List.of(), List.of(), List.of(), List.of(), detail);
    }

    public static RuleExecutionResult hit(List<ReviewIssue> issues, List<RuleEvidence> evidence, String detail) {
        return new RuleExecutionResult(RuleExecutionStatus.HIT, true, List.of(), List.of(), issues, evidence, detail);
    }

    public static RuleExecutionResult extraction(List<EvidenceValue> codes, List<EvidenceValue> names, String detail) {
        boolean matched = (codes != null && !codes.isEmpty()) || (names != null && !names.isEmpty());
        return new RuleExecutionResult(matched ? RuleExecutionStatus.HIT : RuleExecutionStatus.NOT_HIT,
                matched, codes, names, List.of(), List.of(), detail);
    }

    public static RuleExecutionResult skipped(String detail) {
        return new RuleExecutionResult(RuleExecutionStatus.SKIPPED, false, List.of(), List.of(), List.of(), List.of(), detail);
    }

    public static RuleExecutionResult indeterminate(String detail) {
        return new RuleExecutionResult(RuleExecutionStatus.INDETERMINATE, false, List.of(), List.of(), List.of(), List.of(), detail);
    }

    public static RuleExecutionResult failed(String detail) {
        return new RuleExecutionResult(RuleExecutionStatus.FAILED, false, List.of(), List.of(), List.of(), List.of(), detail);
    }
}
