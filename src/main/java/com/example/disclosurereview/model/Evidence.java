package com.example.disclosurereview.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 单条证据（模型返回结构内）。
 *
 * @param verified 是否已在 PDF 原文中回查命中
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Evidence(
        Integer pageNumber,
        String text,
        Boolean verified
) {
    public Evidence(Integer pageNumber, String text) {
        this(pageNumber, text, null);
    }

    public Evidence withVerified(boolean verified) {
        return new Evidence(pageNumber, text, verified);
    }
}
