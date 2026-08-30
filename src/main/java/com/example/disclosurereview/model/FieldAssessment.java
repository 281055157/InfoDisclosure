package com.example.disclosurereview.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** 模型对某个字段的评估结果 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FieldAssessment(
        String value,
        Double confidence,
        List<Evidence> evidence
) {
}
