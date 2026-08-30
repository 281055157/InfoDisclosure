package com.example.disclosurereview.exception;

/** Excel 解析失败 */
public class ExcelParseException extends RuntimeException {
    public ExcelParseException(String message) {
        super(message);
    }

    public ExcelParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
