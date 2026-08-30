package com.example.disclosurereview.llm;

import com.example.disclosurereview.config.LlmProperties;
import com.example.disclosurereview.config.ReviewProperties;
import com.example.disclosurereview.model.DocumentPage;
import com.example.disclosurereview.model.LlmReviewResult;
import com.example.disclosurereview.model.IssueType;
import com.example.disclosurereview.model.MatchBasis;
import com.example.disclosurereview.model.TargetMatchDecision;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.example.disclosurereview.exception.LlmException;

class LlmJsonParsingTest {

    private LlmReviewService service;

    private final List<DocumentPage> pages = List.of(
            new DocumentPage(1, "raw", "产品代码：SGN22555 示例理财丙宁欣天天鎏金现金管理类理财产品3号 投资协议书"),
            new DocumentPage(2, "raw", "第二页正文内容"));

    @BeforeEach
    void setUp() {
        LlmProperties llmProps = new LlmProperties();
        ReviewProperties reviewProps = new ReviewProperties();
        service = new LlmReviewService((LlmClient) null, new DocumentChunker(), new EvidenceVerifier(),
                llmProps, reviewProps, new ObjectMapper());
    }

    @Test
    void parsesValidJson() {
        String json = """
                {
                  "mainProductCode": {"value": "SGN22555", "confidence": 0.95,
                    "evidence": [{"pageNumber": 1, "text": "产品代码：SGN22555"}]},
                  "mainProductName": {"value": "示例理财丙宁欣天天鎏金现金管理类理财产品3号", "confidence": 0.9,
                    "evidence": [{"pageNumber": 1, "text": "示例理财丙宁欣天天鎏金现金管理类理财产品3号"}]},
                  "candidateDocumentType": {"value": "投资协议书", "confidence": 0.88, "reason": "正文为协议条款",
                    "evidence": [{"pageNumber": 1, "text": "投资协议书"}]},
                  "otherProductReferences": [],
                  "issues": [
                    {"issueType": "PRODUCT_NAME_VARIANT", "severity": "LOW", "confidence": 0.6,
                     "pageNumber": 1, "evidenceText": "产品代码：SGN22555",
                     "explanation": "测试", "suggestion": "建议"}
                  ],
                  "summary": "测试摘要",
                  "manualReviewSuggestion": "测试建议"
                }
                """;
        LlmReviewResult result = service.parseAndValidate(json, pages);
        assertThat(result.mainProductCode().value()).isEqualTo("SGN22555");
        assertThat(result.mainProductCode().confidence()).isEqualTo(0.95);
        assertThat(result.mainProductCode().evidence()).hasSize(1);
        assertThat(result.candidateDocumentType().value()).isEqualTo("投资协议书");
        assertThat(result.issues()).hasSize(1);
        assertThat(result.issues().get(0).issueType()).isEqualTo(IssueType.PRODUCT_NAME_VARIANT);
        assertThat(result.summary()).isEqualTo("测试摘要");
    }

    @Test
    void dropsLegacyPlaceholderIssues() {
        String json = """
                {
                  "issues": [
                    {"issueType": "PLACEHOLDER_NOT_REPLACED", "severity": "LOW", "confidence": 0.6,
                     "pageNumber": 1, "evidenceText": "产品代码：SGN22555",
                     "explanation": "旧占位符规则", "suggestion": ""}
                  ]
                }
                """;
        LlmReviewResult result = service.parseAndValidate(json, pages);
        assertThat(result.issues()).isEmpty();
    }

    @Test
    void dropsInvalidEvidenceAndOutOfRangeConfidence() {
        String json = """
                {
                  "mainProductCode": {"value": "SGN22555", "confidence": 1.5,
                    "evidence": [{"pageNumber": 1, "text": "凭空捏造的文本"},
                                 {"pageNumber": 99, "text": "产品代码：SGN22555"}]},
                  "issues": [
                    {"issueType": "POSSIBLE_TEMPLATE_RESIDUE", "severity": "HIGH", "confidence": 0.9,
                     "pageNumber": 1, "evidenceText": "不存在的证据文本",
                     "explanation": "假证据", "suggestion": ""},
                    {"issueType": "INVALID_TYPE", "severity": "HIGH", "confidence": 0.9,
                     "pageNumber": 1, "evidenceText": "产品代码：SGN22555",
                     "explanation": "非法枚举", "suggestion": ""}
                  ]
                }
                """;
        LlmReviewResult result = service.parseAndValidate(json, pages);
        // confidence 超出范围 -> null
        assertThat(result.mainProductCode().confidence()).isNull();
        // 无效证据被删除（捏造文本、页码越界）
        assertThat(result.mainProductCode().evidence()).isEmpty();
        // 假证据问题被删除；非法枚举降级为 UNKNOWN_ISSUE 且证据有效被保留
        assertThat(result.issues()).hasSize(1);
        assertThat(result.issues().get(0).issueType()).isEqualTo(IssueType.UNKNOWN_ISSUE);
    }

    @Test
    void toleratesMarkdownCodeFence() {
        String json = """
                ```json
                {"summary": "带代码块", "issues": []}
                ```
                """;
        LlmReviewResult result = service.parseAndValidate(json, pages);
        assertThat(result.summary()).isEqualTo("带代码块");
    }

    @Test
    void parsesNewTargetProductAssessmentFields() {
        String json = """
                {
                  "documentScope": "SINGLE_PRODUCT",
                  "targetProductAssessment": {
                    "decision": "MATCH",
                    "productIdentityDecision": "PRODUCT_MATCHED",
                    "businessAcceptanceDecision": "ACCEPTABLE",
                    "documentScope": "SINGLE_PRODUCT",
                    "matchBases": ["EXACT_PRODUCT_CODE"],
                    "declaredProductCode": "SGN22555",
                    "matchedProductCode": "SGN22555",
                    "confidence": 0.96,
                    "evidence": [{"pageNumber": 1, "text": "产品代码：SGN22555"}]
                  },
                  "productOccurrences": [
                    {"productCode": "SGN22555", "role": "TARGET_PRODUCT", "pageNumber": 1,
                     "evidenceText": "产品代码：SGN22555", "confidence": 0.9}
                  ],
                  "targetProductRows": [],
                  "agencyAssessment": {
                    "isDistributionAgreement": false,
                    "targetBankIsDistributor": false,
                    "confidence": 0.0,
                    "evidence": []
                  },
                  "issues": []
                }
                """;
        LlmReviewResult result = service.parseAndValidate(json, pages);
        assertThat(result.targetProductAssessment().decision()).isEqualTo(TargetMatchDecision.MATCH);
        assertThat(result.targetProductAssessment().matchBases()).contains(MatchBasis.EXACT_PRODUCT_CODE);
        assertThat(result.productOccurrences()).hasSize(1);
    }

    @Test
    void throwsOnInvalidJson() {
        assertThatThrownBy(() -> service.parseAndValidate("这不是JSON", pages))
                .isInstanceOf(LlmException.class);
    }
}
