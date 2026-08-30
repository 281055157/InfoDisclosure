package com.example.disclosurereview.model;

/** 技术处理状态，描述处理链路本身是否成功，不代表业务风险 */
public enum TechnicalStatus {
    PENDING,
    PROCESSING,
    SUCCESS,
    PDF_ENCRYPTED,
    PDF_PARSE_FAILED,
    EXCEL_PARSE_FAILED,
    PRODUCT_SERVICE_FAILED,
    LLM_TIMEOUT,
    LLM_CALL_FAILED,
    LLM_RESPONSE_INVALID,
    EVIDENCE_VERIFY_FAILED,
    DATABASE_ERROR,
    UNKNOWN_ERROR,
    LLM_FAILED,
    PARTIAL_SUCCESS
}
