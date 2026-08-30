package com.example.disclosurereview.llm;

import java.util.List;

public record LlmAgentProviderResponse(
        String content,
        List<LlmToolCall> toolCalls,
        String rawResponse,
        LlmUsage usage
) {
    public LlmAgentProviderResponse {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        usage = usage == null ? LlmUsage.empty() : usage;
    }
}
