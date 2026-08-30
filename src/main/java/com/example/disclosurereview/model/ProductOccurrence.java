package com.example.disclosurereview.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** 正文中被规则或模型识别到的一次产品出现。 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductOccurrence(
        String productCode,
        String productName,
        ProductReferenceRole role,
        Integer pageNumber,
        String evidenceText,
        Double confidence
) {
}
