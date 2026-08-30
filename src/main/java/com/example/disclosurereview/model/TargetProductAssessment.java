package com.example.disclosurereview.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** 目标产品一致性审核的核心结构化结论。 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TargetProductAssessment(
        TargetMatchDecision decision,
        ProductIdentityDecision productIdentityDecision,
        BusinessAcceptanceDecision businessAcceptanceDecision,
        DocumentScope documentScope,
        List<MatchBasis> matchBases,
        String declaredProductCode,
        String matchedProductCode,
        String matchedProductName,
        String matchedProductSeries,
        String matchedInstitution,
        List<Evidence> evidence,
        Double confidence,
        String explanation,
        String manualReviewSuggestion
) {
    public TargetProductAssessment(
            TargetMatchDecision decision,
            DocumentScope documentScope,
            List<MatchBasis> matchBases,
            String declaredProductCode,
            String matchedProductCode,
            String matchedProductName,
            String matchedProductSeries,
            String matchedInstitution,
            List<Evidence> evidence,
            double confidence,
            String explanation,
            String manualReviewSuggestion) {
        this(decision, identityFrom(decision), acceptanceFrom(decision),
                documentScope, matchBases, declaredProductCode, matchedProductCode,
                matchedProductName, matchedProductSeries, matchedInstitution, evidence,
                confidence, explanation, manualReviewSuggestion);
    }

    public TargetProductAssessment {
        matchBases = matchBases == null ? List.of() : List.copyOf(matchBases);
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }

    public static TargetProductAssessment unknown(String declaredProductCode, String explanation) {
        return new TargetProductAssessment(TargetMatchDecision.UNKNOWN,
                ProductIdentityDecision.PRODUCT_NOT_IDENTIFIED,
                BusinessAcceptanceDecision.UNKNOWN,
                DocumentScope.UNKNOWN,
                List.of(),
                declaredProductCode,
                null,
                null,
                null,
                null,
                List.of(),
                0.0,
                explanation,
                "建议人工复核目标产品与正文内容的对应关系。");
    }

    public static ProductIdentityDecision identityFrom(TargetMatchDecision decision) {
        if (decision == null) {
            return ProductIdentityDecision.PRODUCT_NOT_IDENTIFIED;
        }
        return switch (decision) {
            case MATCH -> ProductIdentityDecision.PRODUCT_MATCHED;
            case CONTAINED -> ProductIdentityDecision.PRODUCT_CONTAINED;
            case MATCH_BY_PRODUCT_FAMILY -> ProductIdentityDecision.PRODUCT_FAMILY_MATCHED;
            case ACCEPTABLE_BY_DISTRIBUTOR, NOT_APPLICABLE -> ProductIdentityDecision.PRODUCT_NOT_APPLICABLE;
            case POSSIBLE_MATCH -> ProductIdentityDecision.PRODUCT_POSSIBLY_MATCHED;
            case MISMATCH -> ProductIdentityDecision.PRODUCT_MISMATCH;
            case INSUFFICIENT_EVIDENCE, UNKNOWN -> ProductIdentityDecision.PRODUCT_NOT_IDENTIFIED;
        };
    }

    public static BusinessAcceptanceDecision acceptanceFrom(TargetMatchDecision decision) {
        if (decision == null) {
            return BusinessAcceptanceDecision.UNKNOWN;
        }
        return switch (decision) {
            case MATCH, CONTAINED, ACCEPTABLE_BY_DISTRIBUTOR -> BusinessAcceptanceDecision.ACCEPTABLE;
            case MATCH_BY_PRODUCT_FAMILY, POSSIBLE_MATCH -> BusinessAcceptanceDecision.ACCEPTABLE_WITH_WARNING;
            case MISMATCH -> BusinessAcceptanceDecision.REJECT_SUGGESTED;
            case INSUFFICIENT_EVIDENCE, NOT_APPLICABLE -> BusinessAcceptanceDecision.MANUAL_REVIEW;
            case UNKNOWN -> BusinessAcceptanceDecision.UNKNOWN;
        };
    }
}
