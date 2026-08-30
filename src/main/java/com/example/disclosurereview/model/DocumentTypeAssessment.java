package com.example.disclosurereview.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** 模型对正文候选文件类型的评估 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DocumentTypeAssessment(
        String value,
        Double confidence,
        String reason,
        List<Evidence> evidence
) {
}
