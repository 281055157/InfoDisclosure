package com.example.disclosurereview.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** 正文中出现的其他产品引用 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductReference(
        String productCode,
        String productName,
        Integer pageNumber,
        String text,
        IssueType assessment,
        Double confidence,
        Boolean verified
) {
    public ProductReference withVerified(boolean verified) {
        return new ProductReference(productCode, productName, pageNumber, text,
                assessment, confidence, verified);
    }
}
