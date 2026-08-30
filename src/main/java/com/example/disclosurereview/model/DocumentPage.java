package com.example.disclosurereview.model;

/** PDF 单页文本，页码从 1 开始 */
public record DocumentPage(
        int pageNumber,
        String rawText,
        String normalizedText
) {
}
