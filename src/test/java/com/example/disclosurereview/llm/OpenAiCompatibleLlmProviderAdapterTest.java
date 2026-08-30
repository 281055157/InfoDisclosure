package com.example.disclosurereview.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiCompatibleLlmProviderAdapterTest {

    private MockWebServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void omitsAuthorizationHeaderWhenApiKeyIsEmpty() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"choices\":[{\"message\":{\"content\":\"{\\\"ok\\\":true}\"}}],\"usage\":{\"prompt_tokens\":3,\"completion_tokens\":2}}"));

        LlmClient.RuntimeModel model = new LlmClient.RuntimeModel(
                server.url("/v1").toString(), "", "intranet-qwen", 0.1,
                Duration.ofSeconds(10), "json_object");
        LlmProviderResponse response = new OpenAiCompatibleLlmProviderAdapter(new ObjectMapper())
                .chatCompletion(model, "system", "user");

        assertThat(response.content()).contains("ok");
        RecordedRequest request = server.takeRequest();
        assertThat(request.getPath()).isEqualTo("/v1/chat/completions");
        assertThat(request.getHeader("Authorization")).isNull();
        assertThat(request.getBody().readUtf8()).contains("\"model\":\"intranet-qwen\"");
    }
}
