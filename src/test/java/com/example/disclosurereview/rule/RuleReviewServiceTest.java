package com.example.disclosurereview.rule;

import com.example.disclosurereview.model.DocumentPage;
import com.example.disclosurereview.model.DocumentType;
import com.example.disclosurereview.model.EvidenceValue;
import com.example.disclosurereview.model.IssueType;
import com.example.disclosurereview.model.Product;
import com.example.disclosurereview.repository.ProductRepository;
import com.example.disclosurereview.strategy.ProductCodeFamilyResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RuleReviewServiceTest {

    private RuleReviewService ruleService;

    @BeforeEach
    void setUp() {
        ProductRepository repository = new ProductRepository(new ObjectMapper());
        repository.add(new Product("SGN22555",
                "示例理财丙宁欣天天鎏金现金管理类理财产品3号",
                List.of("宁欣天天鎏金3号"), "理财产品"));
        ruleService = new RuleReviewService(repository, new ProductCodeFamilyResolver());
    }

    private DocumentPage page(int number, String text) {
        return new DocumentPage(number, text, text);
    }

    @Test
    void extractsCodeAfterLabel() {
        List<EvidenceValue> candidates = ruleService.extractProductCodeCandidates(List.of(
                page(1, "产品代码：SGN22555"),
                page(2, "产品编号: ABC12345"),
                page(3, "产品代码 XYZ99999 其他内容")));
        assertThat(candidates).extracting(EvidenceValue::value)
                .contains("SGN22555", "ABC12345", "XYZ99999");
        assertThat(candidates).allSatisfy(c -> {
            assertThat(c.pageNumber()).isPositive();
            assertThat(c.evidenceText()).isNotBlank();
        });
    }

    @Test
    void extractsKnownMasterDataCodeWithoutLabel() {
        List<EvidenceValue> candidates = ruleService.extractProductCodeCandidates(List.of(
                page(1, "本产品 SGN22555 为现金管理类产品")));
        assertThat(candidates).extracting(EvidenceValue::value).contains("SGN22555");
        assertThat(candidates).filteredOn(c -> c.source().equals("RULE_MASTER_DATA")).isNotEmpty();
    }

    @Test
    void detectsConflictOnlyForStrictSingleProductWhenTargetMissing() {
        RuleReviewService.RuleReviewOutcome outcome = ruleService.review(List.of(
                page(1, "产品代码：SGN22555"),
                page(3, "产品代码：SGN22556")),
                DocumentType.PRODUCT_DESCRIPTION,
                "TARGET9999",
                null);
        assertThat(outcome.issues())
                .anySatisfy(i -> assertThat(i.issueType()).isEqualTo(IssueType.CONTENT_PRODUCT_CODE_CONFLICT));
    }

    @Test
    void doesNotTreatMultiProductAnnouncementCodesAsConflict() {
        RuleReviewService.RuleReviewOutcome outcome = ruleService.review(List.of(
                        page(1, "产品代码：SGN22555"),
                        page(3, "产品代码：SGN22556")),
                DocumentType.NAV_ANNOUNCEMENT,
                "SGN22555",
                null);
        assertThat(outcome.issues()).noneMatch(i -> i.issueType() == IssueType.CONTENT_PRODUCT_CODE_CONFLICT);
    }

    @Test
    void noConflictWhenSingleCode() {
        RuleReviewService.RuleReviewOutcome outcome = ruleService.review(List.of(
                page(1, "产品代码：SGN22555"),
                page(2, "再次提到 SGN22555")));
        assertThat(outcome.issues()).noneMatch(i -> i.issueType() == IssueType.CONTENT_PRODUCT_CODE_CONFLICT);
    }

    @Test
    void templateFieldsAreNotTreatedAsPlaceholderIssues() {
        RuleReviewService.RuleReviewOutcome outcome = ruleService.review(List.of(
                page(1, "产品代码：SGN22555 登记编码【登记编码】 风险等级【风险等级】 发行方式【发行方式】")));
        assertThat(outcome.placeholders()).isEmpty();
        assertThat(outcome.issues()).isEmpty();
    }

    @Test
    void reportsDeclaredProductCodeMissingFromProductMaster() {
        RuleReviewService.RuleReviewOutcome outcome = ruleService.review(List.of(
                        page(1, "示例机构理财产品代理销售协议书")),
                DocumentType.RISK_DISCLOSURE,
                "QQGPJUSCCJJ",
                null);

        assertThat(outcome.issues())
                .anySatisfy(issue -> {
                    assertThat(issue.issueType()).isEqualTo(IssueType.DECLARED_PRODUCT_NOT_FOUND);
                    assertThat(issue.evidenceText()).contains("QQGPJUSCCJJ");
                });
    }

    @Test
    void detectsContradictoryRiskLevelMapping() {
        RuleReviewService.RuleReviewOutcome outcome = ruleService.review(List.of(
                page(4, "示例理财甲产品按照风险程度从低到高分为五级，包括："
                        + "低风险产品（R1）、中低风险产品（R2）、中低风险产品（R3）、"
                        + "中高风险产品（R4）、高风险产品（R5）。")),
                DocumentType.CUSTOMER_RIGHTS_NOTICE,
                null,
                null);

        assertThat(outcome.issues())
                .anySatisfy(issue -> {
                    assertThat(issue.issueType()).isEqualTo(IssueType.CONTENT_LOGIC_CONFLICT);
                    assertThat(issue.evidenceText()).contains("R3").contains("中低风险");
                });
    }

    @Test
    void acceptsConsistentRiskLevelMapping() {
        RuleReviewService.RuleReviewOutcome outcome = ruleService.review(List.of(
                page(4, "示例理财甲产品按照风险程度从低到高分为五级，包括："
                        + "低风险产品（R1）、中低风险产品（R2）、中风险产品（R3）、"
                        + "中高风险产品（R4）、高风险产品（R5）。")),
                DocumentType.CUSTOMER_RIGHTS_NOTICE,
                null,
                null);

        assertThat(outcome.issues())
                .noneMatch(issue -> issue.issueType() == IssueType.CONTENT_LOGIC_CONFLICT);
    }

    @Test
    void acceptsBaseCodeAliasAlongsideDeclaredShareCode() {
        Product target = new Product(
                "ZYFCG0192A",
                "示例理财乙琮简富春199号理财产品",
                List.of("示例理财乙琮简富春199号"),
                "示例理财管理机构乙",
                "示例理财管理机构乙",
                "ZYFCG0192",
                List.of("ZYFCG0192A", "ZYFCG0192B", "ZYFCG0192C", "ZYFCG0192H"),
                List.of("ZYFCG01920"),
                List.of("示例理财乙琮简富春199号"),
                List.of("示例机构股份有限公司"),
                "理财产品");

        ProductRepository repository = new ProductRepository(new ObjectMapper());
        repository.add(target);
        RuleReviewService service = new RuleReviewService(repository, new ProductCodeFamilyResolver());

        RuleReviewService.RuleReviewOutcome outcome = service.review(List.of(
                        page(8, "第二节 理财产品基本情况 产品名称 示例理财乙琮简富春199号理财产品 "
                                + "产品代码：ZYFCG01920 份额销售代码 ZYFCG0192A")),
                DocumentType.PRODUCT_DESCRIPTION,
                "ZYFCG0192A",
                target);

        assertThat(outcome.issues())
                .noneMatch(issue -> issue.issueType() == IssueType.POSSIBLE_TEMPLATE_RESIDUE
                        || issue.issueType() == IssueType.CONTENT_PRODUCT_CODE_CONFLICT);
    }
}
