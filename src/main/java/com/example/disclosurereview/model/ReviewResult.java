package com.example.disclosurereview.model;

import java.time.Instant;
import java.util.List;

/**
 * 统一审核结果，作为 POST/GET 接口的返回体。
 * 所有字段允许为空，以支持技术失败时仍返回部分结果。
 */
public record ReviewResult(
        String taskId,
        TechnicalStatus technicalStatus,
        BusinessRisk businessRisk,
        FileInfo fileInfo,
        DeclaredInfo declaredInfo,
        ProductMasterInfo productMaster,
        RuleResultInfo ruleResult,
        LlmResultInfo llmResult,
        DocumentScope documentScope,
        DocumentTypeAssessment candidateDocumentType,
        TargetProductAssessment targetProductAssessment,
        List<ProductTableRow> targetProductRows,
        List<ProductOccurrence> productOccurrences,
        AgencyAssessment agencyAssessment,
        List<ReviewIssue> mergedIssues,
        String statusDetail,
        Instant createdAt,
        Instant completedAt
) {
    public ReviewResult(String taskId,
                        TechnicalStatus technicalStatus,
                        BusinessRisk businessRisk,
                        FileInfo fileInfo,
                        DeclaredInfo declaredInfo,
                        ProductMasterInfo productMaster,
                        RuleResultInfo ruleResult,
                        LlmResultInfo llmResult,
                        List<ReviewIssue> mergedIssues,
                        String statusDetail,
                        Instant createdAt,
                        Instant completedAt) {
        this(taskId, technicalStatus, businessRisk, fileInfo, declaredInfo, productMaster,
                ruleResult, llmResult, null, null, null, List.of(), List.of(),
                null, mergedIssues, statusDetail, createdAt, completedAt);
    }

    public ReviewResult {
        targetProductRows = targetProductRows == null ? List.of() : List.copyOf(targetProductRows);
        productOccurrences = productOccurrences == null ? List.of() : List.copyOf(productOccurrences);
        mergedIssues = mergedIssues == null ? List.of() : List.copyOf(mergedIssues);
    }

    public record FileInfo(
            String fileName,
            DocumentCategory documentCategory,
            int pageCount
    ) {
    }

    public record DeclaredInfo(
            String productCode,
            String documentType,
            String b9Value
    ) {
    }

    public record ProductMasterInfo(
            boolean matched,
            String productCode,
            String productName,
            List<String> aliases,
            String managerName,
            String issuerName,
            String parentProductCode,
            List<String> shareCodes,
            List<String> codeAliases,
            List<String> seriesNames,
            List<String> distributorNames,
            String productType
    ) {
        public ProductMasterInfo(boolean matched,
                                 String productCode,
                                 String productName,
                                 List<String> aliases) {
            this(matched, productCode, productName, aliases, null, null, null,
                    List.of(), List.of(), List.of(), List.of(), null);
        }

        public ProductMasterInfo {
            aliases = aliases == null ? List.of() : List.copyOf(aliases);
            shareCodes = shareCodes == null ? List.of() : List.copyOf(shareCodes);
            codeAliases = codeAliases == null ? List.of() : List.copyOf(codeAliases);
            seriesNames = seriesNames == null ? List.of() : List.copyOf(seriesNames);
            distributorNames = distributorNames == null ? List.of() : List.copyOf(distributorNames);
        }

        public static ProductMasterInfo notMatched() {
            return new ProductMasterInfo(false, null, null, List.of(), null, null, null,
                    List.of(), List.of(), List.of(), List.of(), null);
        }
    }

    public record RuleResultInfo(
            List<EvidenceValue> productCodeCandidates,
            List<EvidenceValue> productNameCandidates,
            List<EvidenceValue> placeholders,
            List<ReviewIssue> issues
    ) {
        public RuleResultInfo(List<EvidenceValue> productCodeCandidates,
                              List<EvidenceValue> placeholders,
                              List<ReviewIssue> issues) {
            this(productCodeCandidates, List.of(), placeholders, issues);
        }

        public RuleResultInfo {
            productCodeCandidates = productCodeCandidates == null ? List.of() : List.copyOf(productCodeCandidates);
            productNameCandidates = productNameCandidates == null ? List.of() : List.copyOf(productNameCandidates);
            placeholders = placeholders == null ? List.of() : List.copyOf(placeholders);
            issues = issues == null ? List.of() : List.copyOf(issues);
        }

        public static RuleResultInfo empty() {
            return new RuleResultInfo(List.of(), List.of(), List.of(), List.of());
        }
    }

    public record LlmResultInfo(
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
        public LlmResultInfo(FieldAssessment mainProductCode,
                             FieldAssessment mainProductName,
                             DocumentTypeAssessment candidateDocumentType,
                             List<ProductReference> otherProductReferences,
                             List<ReviewIssue> issues,
                             String summary,
                             String manualReviewSuggestion) {
            this(mainProductCode, mainProductName, candidateDocumentType, otherProductReferences,
                    null, null, List.of(), List.of(), null, issues, summary, manualReviewSuggestion);
        }

        public LlmResultInfo {
            otherProductReferences = otherProductReferences == null ? List.of() : List.copyOf(otherProductReferences);
            targetProductRows = targetProductRows == null ? List.of() : List.copyOf(targetProductRows);
            productOccurrences = productOccurrences == null ? List.of() : List.copyOf(productOccurrences);
            issues = issues == null ? List.of() : List.copyOf(issues);
        }

        public static LlmResultInfo empty() {
            return new LlmResultInfo(null, null, null, List.of(), null, null,
                    List.of(), List.of(), null, List.of(), "", "");
        }

        public static LlmResultInfo from(LlmReviewResult r) {
            if (r == null) {
                return empty();
            }
            return new LlmResultInfo(r.mainProductCode(), r.mainProductName(),
                    r.candidateDocumentType(),
                    r.otherProductReferences() == null ? List.of() : r.otherProductReferences(),
                    r.documentScope(),
                    r.targetProductAssessment(),
                    r.targetProductRows(),
                    r.productOccurrences(),
                    r.agencyAssessment(),
                    r.issues() == null ? List.of() : r.issues(),
                    r.summary(), r.manualReviewSuggestion());
        }
    }
}
