package com.example.disclosurereview.llm;

public record LlmProviderResponse(
        String content,
        String rawResponse,
        LlmUsage usage
) {
}
