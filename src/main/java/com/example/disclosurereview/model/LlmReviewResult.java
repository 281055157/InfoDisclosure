package com.example.disclosurereview.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** 模型返回的整体审核结果 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LlmReviewResult(
        FieldAssessment mainProductCode,
        FieldAssessment mainProductName,
        DocumentTypeAssessment candidateDocumentType,
        List<ProductReference> otherProductReferences,
        DocumentScope documentScope,
        TargetProductAssessment targetProductAssessment,
        List<ProductTableRow> targetProductRows,
        List<ProductOccurrence> productOccurrences,
        AgencyAssessment agencyAssessment,
        List<ReviewIssue> issues,
        String summary,
        String manualReviewSuggestion
) {
    public LlmReviewResult(FieldAssessment mainProductCode,
                           FieldAssessment mainProductName,
                           DocumentTypeAssessment candidateDocumentType,
                           List<ProductReference> otherProductReferences,
                           List<ReviewIssue> issues,
                           String summary,
                           String manualReviewSuggestion) {
        this(mainProductCode, mainProductName, candidateDocumentType, otherProductReferences,
                null, null, List.of(), List.of(), null, issues, summary, manualReviewSuggestion);
    }

    public LlmReviewResult {
        otherProductReferences = otherProductReferences == null ? List.of() : List.copyOf(otherProductReferences);
        targetProductRows = targetProductRows == null ? List.of() : List.copyOf(targetProductRows);
        productOccurrences = productOccurrences == null ? List.of() : List.copyOf(productOccurrences);
        issues = issues == null ? List.of() : List.copyOf(issues);
    }

    public static LlmReviewResult empty() {
        return new LlmReviewResult(null, null, null, List.of(), null, null,
                List.of(), List.of(), null, List.of(), "", "");
    }
}
