package com.example.disclosurereview.model;

/** 文件名解析结果 */
public record FileNameInfo(
        String originalFileName,
        String productCode,
        String declaredDocumentType
) {
}
