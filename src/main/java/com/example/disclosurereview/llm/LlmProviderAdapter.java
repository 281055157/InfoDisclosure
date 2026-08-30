package com.example.disclosurereview.llm;

public interface LlmProviderAdapter {

    boolean supports(String providerType);

    LlmProviderResponse chatCompletion(LlmClient.RuntimeModel runtimeModel,
                                       String systemPrompt,
                                       String userPrompt);

    default boolean supportsNativeToolCalling() {
        return false;
    }

    default LlmAgentProviderResponse agentCompletion(LlmClient.RuntimeModel runtimeModel,
                                                     String systemPrompt,
                                                     java.util.List<LlmAgentMessage> messages,
                                                     java.util.List<LlmToolDefinition> tools) {
        throw new com.example.disclosurereview.exception.LlmException("Provider adapter does not support native tool calling");
    }
}
