package com.example.disclosurereview.llm;

public record LlmUsage(
        Integer inputTokens,
        Integer outputTokens,
        Integer cacheHitTokens,
        String rawUsageJson
) {
    public static LlmUsage empty() {
        return new LlmUsage(null, null, 0, null);
    }
}
