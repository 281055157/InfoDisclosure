package com.example.disclosurereview.governance.domain;

import java.util.List;

public record RuleBacktestResult(
        String candidateHash,
        int sampleCount,
        int falsePositiveSampleCount,
        int confirmedPositiveSampleCount,
        int normalSampleCount,
        int oldRuleHitCount,
        int candidateRuleHitCount,
        int resolvedFalsePositiveCount,
        int remainingFalsePositiveCount,
        int lostConfirmedPositiveCount,
        int newUnexpectedHitCount,
        int unresolvedSampleCount,
        BacktestRiskLevel riskLevel,
        BacktestExecutionStatus executionStatus,
        String executorType,
        int determinateSampleCount,
        int llmCallCount,
        long llmInputTokens,
        long llmOutputTokens,
        long llmCacheHitTokens,
        int uniqueDocumentCount,
        List<String> coverageWarnings,
        List<RuleBacktestSampleResult> details
) {
    public RuleBacktestResult(String candidateHash,
                              int sampleCount,
                              int falsePositiveSampleCount,
                              int confirmedPositiveSampleCount,
                              int normalSampleCount,
                              int oldRuleHitCount,
                              int candidateRuleHitCount,
                              int resolvedFalsePositiveCount,
                              int remainingFalsePositiveCount,
                              int lostConfirmedPositiveCount,
                              int newUnexpectedHitCount,
                              int unresolvedSampleCount,
                              BacktestRiskLevel riskLevel,
                              List<RuleBacktestSampleResult> details) {
        this(candidateHash, sampleCount, falsePositiveSampleCount, confirmedPositiveSampleCount,
                normalSampleCount, oldRuleHitCount, candidateRuleHitCount, resolvedFalsePositiveCount,
                remainingFalsePositiveCount, lostConfirmedPositiveCount, newUnexpectedHitCount,
                unresolvedSampleCount, riskLevel,
                unresolvedSampleCount == 0 ? BacktestExecutionStatus.COMPLETED : BacktestExecutionStatus.PARTIAL,
                null, Math.max(0, sampleCount - unresolvedSampleCount), 0, 0, 0, 0,
                sampleCount, List.of(), details);
    }

    public RuleBacktestResult {
        coverageWarnings = coverageWarnings == null ? List.of() : List.copyOf(coverageWarnings);
        details = details == null ? List.of() : List.copyOf(details);
    }
}
