package com.example.disclosurereview.llm;

import com.example.disclosurereview.config.LlmProperties;
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

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class LlmClient {

    private final WebClient webClient;
    private final LlmProperties properties;
    private final ObjectMapper objectMapper;

    public LlmClient(WebClient llmWebClient, LlmProperties properties, ObjectMapper objectMapper) {
        this.webClient = llmWebClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public String chatCompletion(String systemPrompt, String userPrompt) {
        RuntimeModel runtimeModel = new RuntimeModel(
                properties.getBaseUrl(),
                properties.getApiKey(),
                properties.getModel(),
                properties.getTemperature(),
                properties.getTimeout(),
                "json_object");
        return chatCompletion(webClient, runtimeModel, systemPrompt, userPrompt);
    }

    public String chatCompletion(RuntimeModel runtimeModel, String systemPrompt, String userPrompt) {
        WebClient dynamicClient = WebClient.builder()
                .baseUrl(runtimeModel.baseUrl())
                .clientConnector(new ReactorClientHttpConnector(
                        HttpClient.create().responseTimeout(runtimeModel.timeout())))
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();
        return chatCompletion(dynamicClient, runtimeModel, systemPrompt, userPrompt);
    }

    private String chatCompletion(WebClient client,
                                  RuntimeModel runtimeModel,
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

        String raw;
        try {
            var spec = client.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON);
            if (StringUtils.hasText(runtimeModel.apiKey())) {
                spec = spec.header("Authorization", "Bearer " + runtimeModel.apiKey());
            }
            raw = spec.bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(runtimeModel.timeout());
        } catch (WebClientResponseException e) {
            throw new LlmException("LLM HTTP error: " + e.getStatusCode(), e);
        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmException("LLM call failed: " + e.getMessage(), e);
        }

        if (!StringUtils.hasText(raw)) {
            throw new LlmException("LLM returned an empty response");
        }
        try {
            JsonNode root = objectMapper.readTree(raw);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || !StringUtils.hasText(content.asText())) {
                throw new LlmException("LLM response missing choices[0].message.content");
            }
            return content.asText();
        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmException("LLM response parse failed: " + e.getMessage(), e);
        }
    }

    public record RuntimeModel(
            String baseUrl,
            String apiKey,
            String modelName,
            double temperature,
            Duration timeout,
            String responseFormat
    ) {
    }
}
