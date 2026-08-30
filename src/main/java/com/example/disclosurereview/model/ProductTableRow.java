package com.example.disclosurereview.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** 净值公告或汇总表格中定位到的目标产品记录。 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductTableRow(
        String productName,
        String registrationCode,
        String productCode,
        String salesCode,
        String valuationDate,
        String unitNav,
        String accumulatedNav,
        Integer pageNumber,
        String evidenceText,
        Double confidence
) {
}
