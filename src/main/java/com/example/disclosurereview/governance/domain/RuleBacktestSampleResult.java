package com.example.disclosurereview.governance.domain;

public record RuleBacktestSampleResult(
        Long taskId,
        String sampleType,
        Boolean oldRuleMatched,
        Boolean candidateRuleMatched,
        String status,
        String detail,
        Integer pageNumber,
        String evidenceText,
        String explanation,
        int segmentCount,
        String sampleId,
        Long feedbackId,
        Long issueId
) {
    public RuleBacktestSampleResult(Long taskId,
                                    String sampleType,
                                    Boolean oldRuleMatched,
                                    Boolean candidateRuleMatched,
                                    String status,
                                    String detail,
                                    Integer pageNumber,
                                    String evidenceText,
                                    String explanation,
                                    int segmentCount) {
        this(taskId, sampleType, oldRuleMatched, candidateRuleMatched, status, detail,
                pageNumber, evidenceText, explanation, segmentCount, null, null, null);
    }

    public RuleBacktestSampleResult(Long taskId,
                                    String sampleType,
                                    Boolean oldRuleMatched,
                                    Boolean candidateRuleMatched,
                                    String status,
                                    String detail) {
        this(taskId, sampleType, oldRuleMatched, candidateRuleMatched, status, detail,
                null, null, null, 0, null, null, null);
    }
}
