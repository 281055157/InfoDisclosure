package com.example.disclosurereview.exception;

/** 大模型调用或响应处理失败 */
public class LlmException extends RuntimeException {
    public LlmException(String message) {
        super(message);
    }

    public LlmException(String message, Throwable cause) {
        super(message, cause);
    }
}
