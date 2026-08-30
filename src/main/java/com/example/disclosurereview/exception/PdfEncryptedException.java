package com.example.disclosurereview.exception;

/** PDF 已加密，无法读取 */
public class PdfEncryptedException extends RuntimeException {
    public PdfEncryptedException(String message) {
        super(message);
    }

    public PdfEncryptedException(String message, Throwable cause) {
        super(message, cause);
    }
}
