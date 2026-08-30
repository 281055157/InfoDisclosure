package com.example.disclosurereview.llm;

import com.example.disclosurereview.model.DocumentPage;
import com.example.disclosurereview.model.Evidence;
import com.example.disclosurereview.model.ReviewIssue;
import com.example.disclosurereview.model.IssueType;
import com.example.disclosurereview.model.Severity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EvidenceVerifierTest {

    private final EvidenceVerifier verifier = new EvidenceVerifier();

    private final List<DocumentPage> pages = List.of(
            new DocumentPage(1, "raw", "产品代码：SGN22555 示例理财丙宁欣天天鎏金现金管理类理财产品3号"),
            new DocumentPage(2, "raw", "第二页：投资协议书正文"));

    @Test
    void verifiesExistingEvidence() {
        assertThat(verifier.verifyText(1, "产品代码：SGN22555", pages)).isTrue();
        assertThat(verifier.verifyText(2, "投资协议书", pages)).isTrue();
    }

    @Test
    void toleratesWhitespaceAndPunctuationDifferences() {
        // 模型返回的文本带不同空白/标点也能命中
        assertThat(verifier.verifyText(1, "产品代码 : SGN22555", pages)).isTrue();
        assertThat(verifier.verifyText(1, "产品代码： SGN22555\n示例理财丙", pages)).isTrue();
    }

    @Test
    void rejectsFabricatedEvidence() {
        assertThat(verifier.verifyText(1, "产品代码：SGN99999", pages)).isFalse();
        assertThat(verifier.verifyText(3, "任意文本", pages)).isFalse();
        assertThat(verifier.verifyText(null, "任意文本", pages)).isFalse();
        assertThat(verifier.verifyText(1, "", pages)).isFalse();
    }

    @Test
    void verifyIssueMarksFlag() {
        ReviewIssue good = new ReviewIssue(IssueType.PRODUCT_NAME_VARIANT, Severity.LOW,
                0.6, 1, "产品代码：SGN22555", null, null, "LLM", null);
        assertThat(verifier.verifyIssue(good, pages).verified()).isTrue();

        ReviewIssue bad = new ReviewIssue(IssueType.PRODUCT_NAME_VARIANT, Severity.LOW,
                0.6, 1, "凭空生成的文本", null, null, "LLM", null);
        assertThat(verifier.verifyIssue(bad, pages).verified()).isFalse();
    }

    @Test
    void verifyEvidenceList() {
        List<Evidence> list = List.of(
                new Evidence(1, "示例理财丙宁欣天天鎏金现金管理类理财产品3号"),
                new Evidence(1, "不存在的产品名称"));
        List<Evidence> verified = verifier.verifyEvidenceList(list, pages);
        assertThat(verified.get(0).verified()).isTrue();
        assertThat(verified.get(1).verified()).isFalse();
    }
}
