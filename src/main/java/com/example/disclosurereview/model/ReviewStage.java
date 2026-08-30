package com.example.disclosurereview.model;

/** 审核流水线阶段。 */
public enum ReviewStage {
    FILE_STORED,
    DOCUMENT_PARSING,
    DECLARATION_RESOLVING,
    PRODUCT_MATCHING,
    RULE_REVIEWING,
    LLM_REVIEWING,
    EVIDENCE_VERIFYING,
    RESULT_MERGING,
    WAITING_MANUAL_REVIEW
}
