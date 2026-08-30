package com.example.disclosurereview.model;

/** 展示给人工审核台的证据链条目。 */
public record DecisionEvidence(
        String evidenceId,
        EvidenceSource source,
        String fieldType,
        String extractedValue,
        Integer pageNumber,
        String sheetName,
        String cellAddress,
        String evidenceText,
        boolean verified,
        Double confidence,
        String ruleCode,
        Long modelCallId
) {
}
