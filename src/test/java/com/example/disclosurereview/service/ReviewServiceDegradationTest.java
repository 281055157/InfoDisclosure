package com.example.disclosurereview.service;

import com.example.disclosurereview.TestPdfFactory;
import com.example.disclosurereview.config.LlmProperties;
import com.example.disclosurereview.config.ReviewProperties;
import com.example.disclosurereview.llm.DocumentChunker;
import com.example.disclosurereview.llm.EvidenceVerifier;
import com.example.disclosurereview.llm.LlmClient;
import com.example.disclosurereview.llm.LlmReviewService;
import com.example.disclosurereview.model.BusinessRisk;
import com.example.disclosurereview.model.DocumentCategory;
import com.example.disclosurereview.model.Product;
import com.example.disclosurereview.model.ReviewResult;
import com.example.disclosurereview.model.TechnicalStatus;
import com.example.disclosurereview.parser.ExcelParameterParser;
import com.example.disclosurereview.parser.FileNameParser;
import com.example.disclosurereview.parser.PdfDocumentParser;
import com.example.disclosurereview.repository.ProductRepository;
import com.example.disclosurereview.strategy.ProductCodeFamilyResolver;
import com.example.disclosurereview.strategy.DocumentTypeAliasResolver;
import com.example.disclosurereview.repository.ReviewTaskRepository;
import com.example.disclosurereview.rule.RuleReviewService;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 模型失败降级测试：LLM 接口不可用时，服务仍返回规则审核结果。
 * 使用 MockWebServer，不调用真实模型。
 */
class ReviewServiceDegradationTest {

    private MockWebServer server;
    private ReviewService reviewService;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();

        ObjectMapper objectMapper = new ObjectMapper();
        ProductRepository productRepository = new ProductRepository(objectMapper);
        productRepository.load();
        productRepository.add(new Product("SGN22555",
                "示例理财丙宁欣天天鎏金现金管理类理财产品3号",
                java.util.List.of("宁欣天天鎏金3号"),
                "理财产品"));

        LlmProperties llmProps = new LlmProperties();
        llmProps.setBaseUrl(server.url("/v1").toString());
        llmProps.setModel("test-model");
        llmProps.setTimeout(Duration.ofSeconds(5));
        WebClient webClient = WebClient.builder()
                .baseUrl(llmProps.getBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(HttpClient.create()))
                .build();
        LlmClient llmClient = new LlmClient(webClient, llmProps, objectMapper);
        ReviewProperties reviewProperties = new ReviewProperties();
        reviewProperties.setAllowedDocumentTypes(java.util.List.of("投资协议书", "产品说明书", "发行公告"));

        LlmReviewService llmReviewService = new LlmReviewService(llmClient, new DocumentChunker(),
                new EvidenceVerifier(), llmProps, reviewProperties, objectMapper);

        reviewService = new ReviewService(
                new PdfDocumentParser(),
                new ExcelParameterParser(reviewProperties),
                new FileNameParser(),
                productRepository,
                new RuleReviewService(productRepository, new ProductCodeFamilyResolver()),
                llmReviewService,
                new ReviewResultMerger(new DocumentTypeAliasResolver(reviewProperties)),
                new ReviewTaskRepository(),
                objectMapper);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    private MockMultipartFile pdfFile() {
        try (InputStream in = TestPdfFactory.pdfWithPages(
                "示例理财丙宁欣天天鎏金现金管理类理财产品3号 投资协议书\n产品代码：SGN22555",
                "第二条 产品代码：SGN22555 待填写")) {
            return new MockMultipartFile("file", "SGN22555_投资协议书.pdf",
                    "application/pdf", in.readAllBytes());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void fallsBackToRuleResultWhenLlmDown() {
        // LLM 一直返回 500
        for (int i = 0; i < 3; i++) {
            server.enqueue(new MockResponse().setResponseCode(500).setBody("down"));
        }
        ReviewResult result = reviewService.review(pdfFile(), null, DocumentCategory.AUTO, null, null);

        assertThat(result.technicalStatus()).isEqualTo(TechnicalStatus.LLM_FAILED);
        // 技术失败不被标记为业务高风险
        assertThat(result.businessRisk()).isNotEqualTo(BusinessRisk.HIGH);
        // 规则结果保留
        assertThat(result.ruleResult().productCodeCandidates())
                .extracting(c -> c.value()).contains("SGN22555");
        assertThat(result.declaredInfo().productCode()).isEqualTo("SGN22555");
        assertThat(result.declaredInfo().documentType()).isEqualTo("投资协议书");
        assertThat(result.fileInfo().documentCategory()).isEqualTo(DocumentCategory.PROTOCOL);
        assertThat(result.productMaster().matched()).isTrue();
        assertThat(result.fileInfo().pageCount()).isEqualTo(2);
        // 可通过 taskId 查询
        assertThat(reviewService.findById(result.taskId())).isPresent();
    }

    @Test
    void succeedsWhenLlmUp() {
        String llmJson = """
                {
                  "mainProductCode": {"value": "SGN22555", "confidence": 0.95,
                    "evidence": [{"pageNumber": 1, "text": "产品代码：SGN22555"}]},
                  "mainProductName": {"value": "示例理财丙宁欣天天鎏金现金管理类理财产品3号", "confidence": 0.9,
                    "evidence": [{"pageNumber": 1, "text": "示例理财丙宁欣天天鎏金现金管理类理财产品3号"}]},
                  "candidateDocumentType": {"value": "投资协议书", "confidence": 0.9, "reason": "协议条款",
                    "evidence": [{"pageNumber": 1, "text": "投资协议书"}]},
                  "otherProductReferences": [],
                  "issues": [],
                  "summary": "正文与声明一致",
                  "manualReviewSuggestion": "无需人工介入"
                }
                """;
        String body = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":"
                + toJsonString(llmJson) + "}}]}";
        server.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json").setBody(body));

        ReviewResult result = reviewService.review(pdfFile(), null, DocumentCategory.AUTO, null, null);
        assertThat(result.technicalStatus()).isEqualTo(TechnicalStatus.SUCCESS);
        assertThat(result.llmResult().mainProductCode().value()).isEqualTo("SGN22555");
        assertThat(result.llmResult().candidateDocumentType().value()).isEqualTo("投资协议书");
    }

    @Test
    void autoPrefersAnnouncementTypeWhenB9Present() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("down"));
        MockMultipartFile parameterFile = new MockMultipartFile("parameterFile", "参数表.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                excelWithB9("成立公告"));

        ReviewResult result = reviewService.review(pdfFile(), parameterFile, DocumentCategory.AUTO, null, null);

        assertThat(result.fileInfo().documentCategory()).isEqualTo(DocumentCategory.ANNOUNCEMENT);
        assertThat(result.declaredInfo().documentType()).isEqualTo("成立公告");
        assertThat(result.declaredInfo().b9Value()).isEqualTo("成立公告");
    }

    private String toJsonString(String s) {
        try {
            return new ObjectMapper().writeValueAsString(s);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] excelWithB9(String b9Value) throws Exception {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("参数表");
            var row = sheet.createRow(8);
            row.createCell(1).setCellValue(b9Value);
            workbook.write(out);
            return out.toByteArray();
        }
    }
}
