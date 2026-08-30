package com.example.disclosurereview.model;

/** 目标产品在正文中的支持程度。 */
public enum TargetMatchDecision {
    MATCH,
    CONTAINED,
    MATCH_BY_PRODUCT_FAMILY,
    ACCEPTABLE_BY_DISTRIBUTOR,
    POSSIBLE_MATCH,
    MISMATCH,
    INSUFFICIENT_EVIDENCE,
    NOT_APPLICABLE,
    UNKNOWN
}
