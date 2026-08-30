package com.example.disclosurereview.model;

/** Excel 参数表 B9 单元格读取结果 */
public record ExcelParameterResult(
        String sheetName,
        String cellAddress,
        String rawValue,
        String normalizedValue
) {
}
