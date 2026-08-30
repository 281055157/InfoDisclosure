package com.example.disclosurereview.llm;

import com.example.disclosurereview.config.LlmProperties;
import com.example.disclosurereview.exception.LlmException;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.io.IOException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmClientTest {

    private MockWebServer server;
    private LlmClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        LlmProperties props = new LlmProperties();
        props.setBaseUrl(server.url("/v1").toString());
        // Tests intentionally run without a credential value.
        props.setApiKey("");
        props.setModel("qwen3");
        props.setTimeout(Duration.ofSeconds(10));
        WebClient webClient = WebClient.builder()
                .baseUrl(props.getBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(HttpClient.create()))
                .build();
        client = new LlmClient(webClient, props, new ObjectMapper());
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void returnsContentOnSuccess() throws Exception {
        String body = """
                {"choices": [{"message": {"role": "assistant", "content": "{\\"summary\\": \\"ok\\"}"}}]}
                """;
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(body));

        String content = client.chatCompletion("system", "user");
        assertThat(content).contains("ok");

        RecordedRequest request = server.takeRequest();
        assertThat(request.getPath()).isEqualTo("/v1/chat/completions");
        assertThat(request.getHeader("Authorization")).isNull();
        assertThat(request.getBody().readUtf8()).contains("\"model\":\"qwen3\"");
    }

    @Test
    void throwsOnHttpError() {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("internal error"));
        assertThatThrownBy(() -> client.chatCompletion("s", "u"))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("500");
    }

    @Test
    void throwsOnMalformedResponse() {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"unexpected\": true}"));
        assertThatThrownBy(() -> client.chatCompletion("s", "u"))
                .isInstanceOf(LlmException.class);
    }
}
