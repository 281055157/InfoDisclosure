package com.example.disclosurereview.exception;

/** PDF 解析失败（损坏、非 PDF 等） */
public class PdfParseException extends RuntimeException {
    public PdfParseException(String message) {
        super(message);
    }

    public PdfParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
