package com.example.disclosurereview.llm;

import java.util.List;

public record LlmAgentMessage(
        String role,
        String content,
        String toolCallId,
        List<LlmToolCall> toolCalls
) {
    public static LlmAgentMessage user(String content) { return new LlmAgentMessage("user", content, null, List.of()); }
    public static LlmAgentMessage assistant(String content, List<LlmToolCall> calls) { return new LlmAgentMessage("assistant", content, null, calls == null ? List.of() : calls); }
    public static LlmAgentMessage tool(String id, String content) { return new LlmAgentMessage("tool", content, id, List.of()); }
}
