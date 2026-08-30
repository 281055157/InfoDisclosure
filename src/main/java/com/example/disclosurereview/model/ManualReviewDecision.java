package com.example.disclosurereview.model;

/** 人工审核最终结论。 */
public enum ManualReviewDecision {
    APPROVED,
    APPROVED_WITH_WARNING,
    RETURNED,
    REJECTED,
    UNABLE_TO_CONFIRM
}
