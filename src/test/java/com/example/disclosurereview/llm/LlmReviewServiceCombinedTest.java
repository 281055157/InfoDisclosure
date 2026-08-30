package com.example.disclosurereview.llm;

import com.example.disclosurereview.config.LlmProperties;
import com.example.disclosurereview.config.ReviewProperties;
import com.example.disclosurereview.model.DocumentPage;
import com.example.disclosurereview.rule.domain.RuleAction;
import com.example.disclosurereview.rule.domain.SemanticRuleCheck;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LlmReviewServiceCombinedTest {

    private MockWebServer server;
    private LlmReviewService service;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        objectMapper = new ObjectMapper();

        LlmProperties llmProps = new LlmProperties();
        llmProps.setBaseUrl(server.url("/v1").toString());
        llmProps.setModel("test-model");
        llmProps.setTimeout(Duration.ofSeconds(10));
        llmProps.setMaxInputChars(30000);

        WebClient webClient = WebClient.builder()
                .baseUrl(llmProps.getBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(HttpClient.create()))
                .build();
        LlmClient llmClient = new LlmClient(webClient, llmProps, objectMapper);

        ReviewProperties reviewProperties = new ReviewProperties();
        reviewProperties.setAllowedDocumentTypes(List.of("投资协议书", "产品说明书", "发行公告"));

        service = new LlmReviewService(llmClient, new DocumentChunker(), new EvidenceVerifier(),
                llmProps, reviewProperties, objectMapper);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void combinesMainReviewAndSemanticRulesInSingleRequest() throws Exception {
        String llmJson = """
                {
                  "reviewResult": {
                    "mainProductCode": {"value": "SGN22555", "confidence": 0.95,
                      "evidence": [{"pageNumber": 1, "text": "产品代码：SGN22555"}]},
                    "mainProductName": {"value": "示例理财丙宁欣天天鎏金现金管理类理财产品3号", "confidence": 0.91,
                      "evidence": [{"pageNumber": 1, "text": "示例理财丙宁欣天天鎏金现金管理类理财产品3号"}]},
                    "candidateDocumentType": {"value": "投资协议书", "confidence": 0.9, "reason": "正文标题",
                      "evidence": [{"pageNumber": 1, "text": "投资协议书"}]},
                    "otherProductReferences": [],
                    "issues": [],
                    "summary": "正文与声明一致",
                    "manualReviewSuggestion": "无需人工介入"
                  },
                  "semanticRuleResults": [
                    {
                      "ruleCode": "TEST_LLM_TEXT_LOGIC",
                      "violated": false,
                      "confidence": 0.88,
                      "pageNumber": 1,
                      "evidenceText": "产品代码：SGN22555",
                      "explanation": "未发现语义冲突",
                      "suggestion": "无需处理"
                    }
                  ]
                }
                """;
        server.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(openAiBody(llmJson)));

        List<DocumentPage> pages = List.of(new DocumentPage(1,
                "示例理财丙宁欣天天鎏金现金管理类理财产品3号 投资协议书\n产品代码：SGN22555",
                "示例理财丙宁欣天天鎏金现金管理类理财产品3号 投资协议书\n产品代码：SGN22555"));
        SemanticRuleCheck semanticCheck = new SemanticRuleCheck(
                1L,
                "TEST_LLM_TEXT_LOGIC",
                11L,
                "v1",
                101L,
                "LLM_POLICY",
                "检查正文是否存在逻辑冲突",
                "不得出现产品代码和产品名称不一致",
                "JSON",
                0.7,
                RuleAction.defaultAction(),
                List.of());

        LlmGatewayResponse<CombinedLlmReviewResult> response = service.reviewCombined(
                pages,
                "SGN22555_投资协议书.pdf",
                "PROTOCOL",
                "SGN22555",
                "投资协议书",
                null,
                "{}",
                "{}",
                "[]",
                "投资协议书",
                "",
                "[]",
                "[]",
                "[]",
                List.of(semanticCheck),
                LlmCallContext.none());

        assertThat(response.result().reviewResult().mainProductCode().value()).isEqualTo("SGN22555");
        assertThat(response.result().semanticRuleResults())
                .singleElement()
                .satisfies(result -> {
                    assertThat(result.ruleCode()).isEqualTo("TEST_LLM_TEXT_LOGIC");
                    assertThat(result.violated()).isFalse();
                });

        RecordedRequest request = server.takeRequest();
        assertThat(server.getRequestCount()).isEqualTo(1);
        assertThat(request.getBody().readUtf8())
                .contains("语义规则列表")
                .contains("semanticRuleResults")
                .contains("TEST_LLM_TEXT_LOGIC");
    }

    private String openAiBody(String content) throws Exception {
        return "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":"
                + objectMapper.writeValueAsString(content) + "}}]}";
    }
}
