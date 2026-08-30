package com.example.disclosurereview.llm;

import com.example.disclosurereview.exception.LlmException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.netty.http.client.HttpClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class OpenAiCompatibleLlmProviderAdapter implements LlmProviderAdapter {

    private final ObjectMapper objectMapper;

    public OpenAiCompatibleLlmProviderAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(String providerType) {
        return !StringUtils.hasText(providerType) || "OPENAI_COMPATIBLE".equalsIgnoreCase(providerType);
    }

    @Override
    public LlmProviderResponse chatCompletion(LlmClient.RuntimeModel runtimeModel,
                                              String systemPrompt,
                                              String userPrompt) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", runtimeModel.modelName());
        body.put("temperature", runtimeModel.temperature());
        if (StringUtils.hasText(runtimeModel.responseFormat())) {
            body.put("response_format", Map.of("type", runtimeModel.responseFormat()));
        }
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)));

        String raw = post(runtimeModel, body);
        if (!StringUtils.hasText(raw)) {
            throw new LlmException("LLM returned an empty response");
        }
        try {
            JsonNode root = objectMapper.readTree(raw);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || !StringUtils.hasText(content.asText())) {
                throw new LlmException("LLM response missing choices[0].message.content");
            }
            return new LlmProviderResponse(content.asText(), raw, usage(root.path("usage")));
        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmException("LLM response parse failed: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean supportsNativeToolCalling() {
        return true;
    }

    @Override
    public LlmAgentProviderResponse agentCompletion(LlmClient.RuntimeModel runtimeModel,
                                                    String systemPrompt,
                                                    List<LlmAgentMessage> messages,
                                                    List<LlmToolDefinition> tools) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", runtimeModel.modelName());
        body.put("temperature", runtimeModel.temperature());
        List<Map<String, Object>> requestMessages = new java.util.ArrayList<>();
        requestMessages.add(Map.of("role", "system", "content", systemPrompt));
        for (LlmAgentMessage message : messages) {
            Map<String, Object> encoded = new LinkedHashMap<>();
            encoded.put("role", message.role());
            if (message.content() != null) encoded.put("content", message.content());
            if (StringUtils.hasText(message.toolCallId())) encoded.put("tool_call_id", message.toolCallId());
            if (message.toolCalls() != null && !message.toolCalls().isEmpty()) {
                encoded.put("tool_calls", message.toolCalls().stream().map(call -> Map.of(
                        "id", call.id(), "type", "function", "function", Map.of(
                                "name", call.name(), "arguments", call.arguments().toString()))).toList());
            }
            requestMessages.add(encoded);
        }
        body.put("messages", requestMessages);
        body.put("tools", tools.stream().map(tool -> Map.of(
                "type", "function", "function", Map.of(
                        "name", tool.name(), "description", tool.description(), "parameters", tool.inputSchema()))).toList());
        body.put("tool_choice", "auto");
        String raw = post(runtimeModel, body);
        try {
            JsonNode root = objectMapper.readTree(raw);
            JsonNode message = root.path("choices").path(0).path("message");
            List<LlmToolCall> calls = new java.util.ArrayList<>();
            for (JsonNode toolCall : message.path("tool_calls")) {
                String arguments = toolCall.path("function").path("arguments").asText("{}");
                JsonNode parsed;
                try { parsed = objectMapper.readTree(arguments); }
                catch (Exception e) { parsed = objectMapper.createObjectNode(); }
                calls.add(new LlmToolCall(toolCall.path("id").asText(),
                        toolCall.path("function").path("name").asText(), parsed));
            }
            String content = message.path("content").isTextual() ? message.path("content").asText() : null;
            if (calls.isEmpty() && !StringUtils.hasText(content)) {
                throw new LlmException("LLM response missing content and tool_calls");
            }
            return new LlmAgentProviderResponse(content, calls, raw, usage(root.path("usage")));
        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmException("LLM tool response parse failed: " + e.getMessage(), e);
        }
    }

    private String post(LlmClient.RuntimeModel runtimeModel, Map<String, Object> body) {
        WebClient client = WebClient.builder()
                .baseUrl(runtimeModel.baseUrl())
                .clientConnector(new ReactorClientHttpConnector(
                        HttpClient.create().responseTimeout(runtimeModel.timeout())))
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();
        try {
            var spec = client.post().uri("/chat/completions").contentType(MediaType.APPLICATION_JSON);
            if (StringUtils.hasText(runtimeModel.apiKey())) spec = spec.header("Authorization", "Bearer " + runtimeModel.apiKey());
            return spec.bodyValue(body).retrieve().bodyToMono(String.class).block(runtimeModel.timeout());
        } catch (WebClientResponseException e) {
            throw new LlmException("LLM HTTP error: " + e.getStatusCode(), e);
        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmException("LLM call failed: " + e.getMessage(), e);
        }
    }

    private LlmUsage usage(JsonNode usage) {
        if (usage == null || usage.isMissingNode() || usage.isNull()) {
            return LlmUsage.empty();
        }
        Integer input = firstInt(usage, "prompt_tokens", "input_tokens");
        Integer output = firstInt(usage, "completion_tokens", "output_tokens");
        Integer cacheHit = firstInt(usage, "cache_hit_tokens", "cached_tokens");
        if (cacheHit == null) {
            cacheHit = firstInt(usage.path("prompt_tokens_details"), "cached_tokens", "cache_hit_tokens");
        }
        if (cacheHit == null) {
            cacheHit = firstInt(usage.path("input_tokens_details"), "cached_tokens", "cache_hit_tokens");
        }
        String rawUsageJson;
        try {
            rawUsageJson = objectMapper.writeValueAsString(usage);
        } catch (Exception e) {
            rawUsageJson = null;
        }
        return new LlmUsage(input, output, cacheHit == null ? 0 : cacheHit, rawUsageJson);
    }

    private Integer firstInt(JsonNode node, String... fields) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (value.isInt() || value.isLong()) {
                return value.asInt();
            }
        }
        return null;
    }
}
