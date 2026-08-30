package com.example.disclosurereview.llm;

import com.example.disclosurereview.model.BusinessRisk;
import com.example.disclosurereview.model.BusinessAcceptanceDecision;
import com.example.disclosurereview.model.DocumentCategory;
import com.example.disclosurereview.model.DocumentScope;
import com.example.disclosurereview.model.DocumentTypeAssessment;
import com.example.disclosurereview.model.FieldAssessment;
import com.example.disclosurereview.model.IssueType;
import com.example.disclosurereview.model.MatchBasis;
import com.example.disclosurereview.model.ProductIdentityDecision;
import com.example.disclosurereview.model.ProductReference;
import com.example.disclosurereview.model.ReviewIssue;
import com.example.disclosurereview.model.ReviewResult;
import com.example.disclosurereview.model.Severity;
import com.example.disclosurereview.model.TargetMatchDecision;
import com.example.disclosurereview.model.TargetProductAssessment;
import com.example.disclosurereview.config.ReviewProperties;
import com.example.disclosurereview.service.ReviewResultMerger;
import com.example.disclosurereview.strategy.DocumentTypeAliasResolver;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewResultMergerTest {

    private final ReviewResultMerger merger = new ReviewResultMerger(
            new DocumentTypeAliasResolver(new ReviewProperties()));

    private ReviewResult build(String declaredCode, String llmMainCode, boolean masterMatched,
                               String masterCode, List<ReviewIssue> ruleIssues,
                               List<ReviewIssue> llmIssues,
                               DocumentTypeAssessment docType, String declaredType,
                               FieldAssessment mainName, String masterName,
                               List<ProductReference> refs) {
        return new ReviewResult(
                "t1", null, null,
                new ReviewResult.FileInfo("f.pdf", DocumentCategory.AUTO, 1),
                new ReviewResult.DeclaredInfo(declaredCode, declaredType, null),
                new ReviewResult.ProductMasterInfo(masterMatched, masterCode, masterName, List.of()),
                new ReviewResult.RuleResultInfo(List.of(), List.of(), ruleIssues),
                new ReviewResult.LlmResultInfo(
                        llmMainCode == null ? null : new FieldAssessment(llmMainCode, 0.9, List.of()),
                        mainName, docType, refs, llmIssues, "", ""),
                List.of(), null, Instant.now(), Instant.now());
    }

    @Test
    void highWhenDeclaredCodeDiffersAndMatchesAnotherProduct() {
        ReviewResult r = build("SGN22555", "SGN22556", true, "SGN22556",
                List.of(), List.of(), null, null, null, null, List.of());
        assertThat(merger.mergeRisk(r)).isEqualTo(BusinessRisk.HIGH);
    }

    @Test
    void highWhenTemplateResidueHighConfidenceVerified() {
        ReviewIssue issue = new ReviewIssue(IssueType.POSSIBLE_TEMPLATE_RESIDUE, Severity.HIGH,
                0.9, 2, "产品代码：SGN99999", "模板残留", "建议", "LLM", true);
        ReviewResult r = build("SGN22555", "SGN22555", true, "SGN22555",
                List.of(), List.of(issue), null, null, null, null, List.of());
        assertThat(merger.mergeRisk(r)).isEqualTo(BusinessRisk.HIGH);
    }

    @Test
    void notHighWhenTemplateResidueUnverified() {
        ReviewIssue issue = new ReviewIssue(IssueType.POSSIBLE_TEMPLATE_RESIDUE, Severity.HIGH,
                0.9, 2, "不存在的证据", "模板残留", "建议", "LLM", false);
        ReviewResult r = build("SGN22555", "SGN22555", true, "SGN22555",
                List.of(), List.of(issue), null, null, null, null, List.of());
        assertThat(merger.mergeRisk(r)).isNotEqualTo(BusinessRisk.HIGH);
    }

    @Test
    void mediumWhenDeclaredTypeMismatch() {
        DocumentTypeAssessment docType = new DocumentTypeAssessment("产品说明书", 0.85, "理由", List.of());
        ReviewResult r = build("SGN22555", "SGN22555", true, "SGN22555",
                List.of(), List.of(), docType, "投资协议书", null, null, List.of());
        assertThat(merger.mergeRisk(r)).isEqualTo(BusinessRisk.MEDIUM);
    }

    @Test
    void doesNotMismatchEquivalentDistributionAgreementAliases() {
        DocumentTypeAssessment docType = new DocumentTypeAssessment(
                "代销协议书", 1.0, "正文标题：示例机构理财产品代理销售协议书", List.of());
        ReviewResult r = build("ZYYJG00010", "ZYYJG00010", true, "ZYYJG00010",
                List.of(), List.of(), docType, "代理销售协议书", null, null, List.of());

        assertThat(merger.mergeIssues(r))
                .noneMatch(issue -> issue.issueType() == IssueType.DECLARED_TYPE_MISMATCH);
        assertThat(merger.mergeRisk(r)).isEqualTo(BusinessRisk.NORMAL);
    }

    @Test
    void mediumWhenDeclaredProductIsMissingFromMaster() {
        ReviewIssue issue = new ReviewIssue(IssueType.DECLARED_PRODUCT_NOT_FOUND, Severity.MEDIUM,
                1.0, null, "声明产品代码：QQGPJUSCCJJ", "未找到", "核实", "RULE", true);
        ReviewResult r = build("QQGPJUSCCJJ", null, false, null,
                List.of(issue), List.of(), null, "风险揭示书", null, null, List.of());

        assertThat(merger.mergeRisk(r)).isEqualTo(BusinessRisk.MEDIUM);
    }

    @Test
    void highWhenContentLogicConflictIsVerified() {
        ReviewIssue issue = new ReviewIssue(IssueType.CONTENT_LOGIC_CONFLICT, Severity.HIGH,
                1.0, 4, "R2中低风险，R3中低风险", "映射矛盾", "核对", "RULE", true);
        ReviewResult r = build("ZYYJG00010", "ZYYJG00010", true, "ZYYJG00010",
                List.of(issue), List.of(), null, "投资者权益须知", null, null, List.of());

        assertThat(merger.mergeRisk(r)).isEqualTo(BusinessRisk.HIGH);
    }

    @Test
    void mediumWhenConflictConfidenceMidRange() {
        ReviewIssue issue = new ReviewIssue(IssueType.CONTENT_PRODUCT_CODE_CONFLICT, Severity.MEDIUM,
                0.7, 1, "产品代码：SGN22555", "冲突", "建议", "LLM", true);
        ReviewResult r = build("SGN22555", "SGN22555", true, "SGN22555",
                List.of(), List.of(issue), null, null, null, null, List.of());
        assertThat(merger.mergeRisk(r)).isEqualTo(BusinessRisk.MEDIUM);
    }

    @Test
    void lowWhenNameVariant() {
        ReviewIssue issue = new ReviewIssue(IssueType.PRODUCT_NAME_VARIANT, Severity.LOW,
                0.8, 1, "宁欣天天鎏金3号", "简称", "建议", "LLM", true);
        ReviewResult r = build("SGN22555", "SGN22555", true, "SGN22555",
                List.of(), List.of(issue), null, null, null, null, List.of());
        assertThat(merger.mergeRisk(r)).isEqualTo(BusinessRisk.LOW);
    }

    @Test
    void normalWhenNoIssues() {
        ReviewResult r = build("SGN22555", "SGN22555", true, "SGN22555",
                List.of(), List.of(), null, null, null, null, List.of());
        assertThat(merger.mergeRisk(r)).isEqualTo(BusinessRisk.NORMAL);
    }

    @Test
    void duplicateIssuesKeepHigherSeverityVersion() {
        ReviewIssue mediumRuleIssue = new ReviewIssue(IssueType.CONTENT_LOGIC_CONFLICT, Severity.MEDIUM,
                0.9, 1, "标题与正文矛盾", "规则语义复核命中", "核实", "LLM_POLICY", true);
        ReviewIssue highLlmIssue = new ReviewIssue(IssueType.CONTENT_LOGIC_CONFLICT, Severity.HIGH,
                0.9, 1, "标题与正文矛盾", "模型主审核命中", "核实", "LLM", true);
        ReviewResult r = build("QQGPJUSDRKN", "QQGPJUSDRKN", true, "QQGPJUSDRKN",
                List.of(mediumRuleIssue), List.of(highLlmIssue), null, "投资协议书", null, null, List.of());

        assertThat(merger.mergeIssues(r))
                .singleElement()
                .satisfies(issue -> {
                    assertThat(issue.severity()).isEqualTo(Severity.HIGH);
                    assertThat(issue.source()).isEqualTo("LLM");
                });
    }

    @Test
    void highRiskDowngradesAcceptableDecisionToManualReview() {
        TargetProductAssessment acceptableTarget = new TargetProductAssessment(
                TargetMatchDecision.CONTAINED,
                ProductIdentityDecision.PRODUCT_CONTAINED,
                BusinessAcceptanceDecision.ACCEPTABLE,
                DocumentScope.GENERAL_AGREEMENT,
                List.of(MatchBasis.EXACT_PRODUCT_CODE),
                "QQGPJUSDRKN",
                "QQGPJUSDRKN",
                "示例理财甲全球配置高评级美元日日开",
                null,
                null,
                List.of(),
                0.9,
                "目标产品包含于通用协议",
                "人工确认标题与正文一致性");
        ReviewIssue highIssue = new ReviewIssue(IssueType.CONTENT_LOGIC_CONFLICT, Severity.HIGH,
                0.9, 1, "标题与正文矛盾", "模型主审核命中", "核实", "LLM", true);
        ReviewResult r = new ReviewResult(
                "t1", null, null,
                new ReviewResult.FileInfo("f.pdf", DocumentCategory.PROTOCOL, 1),
                new ReviewResult.DeclaredInfo("QQGPJUSDRKN", "投资协议书", null),
                new ReviewResult.ProductMasterInfo(true, "QQGPJUSDRKN",
                        "示例理财甲全球配置高评级美元日日开", List.of()),
                ReviewResult.RuleResultInfo.empty(),
                new ReviewResult.LlmResultInfo(null, null, null, List.of(), List.of(highIssue), "", ""),
                null,
                null,
                acceptableTarget,
                List.of(),
                List.of(),
                null,
                List.of(),
                null,
                Instant.now(),
                Instant.now());

        BusinessRisk risk = merger.mergeRisk(r);
        assertThat(risk).isEqualTo(BusinessRisk.HIGH);
        assertThat(merger.mergeBusinessAcceptance(r, risk)).isEqualTo(BusinessAcceptanceDecision.MANUAL_REVIEW);
    }
}
