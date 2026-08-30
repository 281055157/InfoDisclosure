package com.example.disclosurereview.strategy;

import com.example.disclosurereview.config.ReviewProperties;
import com.example.disclosurereview.model.BusinessAcceptanceDecision;
import com.example.disclosurereview.model.DocumentCategory;
import com.example.disclosurereview.model.DocumentPage;
import com.example.disclosurereview.model.DocumentScope;
import com.example.disclosurereview.model.DocumentType;
import com.example.disclosurereview.model.MatchBasis;
import com.example.disclosurereview.model.Product;
import com.example.disclosurereview.model.ProductIdentityDecision;
import com.example.disclosurereview.model.ProductTableRow;
import com.example.disclosurereview.model.TargetProductAssessment;
import com.example.disclosurereview.model.TargetMatchDecision;
import com.example.disclosurereview.repository.ProductRepository;
import com.example.disclosurereview.rule.RuleReviewService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentReviewStrategyRegistryTest {

    private ProductRepository repository;
    private RuleReviewService ruleService;
    private DocumentReviewStrategyRegistry registry;
    private ProductTableRowExtractor tableRowExtractor;
    private Product targetProduct;

    @BeforeEach
    void setUp() {
        ProductNameNormalizer normalizer = new ProductNameNormalizer();
        ProductSeriesExtractor seriesExtractor = new ProductSeriesExtractor(normalizer);
        ProductCodeFamilyResolver familyResolver = new ProductCodeFamilyResolver();
        ProductNameMatcher matcher = new ProductNameMatcher(normalizer, seriesExtractor);
        InstitutionRoleExtractor roleExtractor = new InstitutionRoleExtractor();
        ReviewProperties properties = new ReviewProperties();
        registry = new DocumentReviewStrategyRegistry(properties, familyResolver, matcher, seriesExtractor, roleExtractor);
        tableRowExtractor = new ProductTableRowExtractor(familyResolver);

        repository = new ProductRepository(new ObjectMapper());
        targetProduct = new Product(
                "ZYBSG0056D",
                "示例理财乙琮简宝石59号理财产品D份额",
                List.of("琮简宝石59号D份额"),
                "示例理财管理机构乙",
                "示例理财管理机构乙",
                "ZYBSG0056",
                List.of("ZYBSG0056A", "ZYBSG0056B", "ZYBSG0056D"),
                List.of(),
                List.of("示例理财乙琮简宝石59号"),
                List.of("示例机构", "示例机构股份有限公司"),
                "理财产品");
        repository.add(targetProduct);
        repository.add(new Product(
                "XYWTL001A",
                "示例理财丁稳添利最短持有期日开固收类理财产品A份额",
                List.of("稳添利最短持有期日开固收类"),
                "示例理财管理机构丁",
                "示例理财管理机构丁",
                null,
                List.of(),
                List.of(),
                List.of("稳添利最短持有期日开固收类"),
                List.of(),
                "理财产品"));
        repository.add(new Product("SGN22555",
                "示例理财丙宁欣天天鎏金现金管理类理财产品3号",
                List.of("宁欣天天鎏金3号"), "理财产品"));
        ruleService = new RuleReviewService(repository, new ProductCodeFamilyResolver());
    }

    @Test
    void exposesStrategiesForTenSupportedDocumentTypes() {
        assertThat(registry.supportedTypes())
                .containsExactlyInAnyOrder(
                        DocumentType.PRODUCT_DESCRIPTION,
                        DocumentType.RISK_DISCLOSURE,
                        DocumentType.DISTRIBUTION_AGREEMENT,
                        DocumentType.INVESTMENT_AGREEMENT,
                        DocumentType.CUSTOMER_RIGHTS_NOTICE,
                        DocumentType.ISSUANCE_ANNOUNCEMENT,
                        DocumentType.PERIODIC_ANNOUNCEMENT,
                        DocumentType.MATURITY_ANNOUNCEMENT,
                        DocumentType.NAV_ANNOUNCEMENT,
                        DocumentType.OTHER_ANNOUNCEMENT);
    }

    @Test
    void productDescriptionSearchesWholeText() {
        Product product = repository.findByCode("SGN22555").orElseThrow();
        var assessment = evaluate(DocumentType.PRODUCT_DESCRIPTION, "SGN22555", product, List.of(
                page(1, "首页只有目录"),
                page(8, "产品基本情况\n产品名称：示例理财丙宁欣天天鎏金现金管理类理财产品3号")));

        assertThat(assessment.decision()).isEqualTo(TargetMatchDecision.MATCH);
        assertThat(assessment.matchBases()).contains(MatchBasis.EXACT_PRODUCT_NAME);
    }

    @Test
    void distributionAgreementAcceptsDistributorRole() {
        var assessment = evaluate(DocumentType.DISTRIBUTION_AGREEMENT, null, null, List.of(
                page(1, "甲方：某理财公司。乙方作为本协议项下理财产品的代理销售机构。乙方：示例机构股份有限公司。")));

        assertThat(assessment.decision()).isEqualTo(TargetMatchDecision.ACCEPTABLE_BY_DISTRIBUTOR);
        assertThat(assessment.productIdentityDecision()).isEqualTo(ProductIdentityDecision.PRODUCT_NOT_APPLICABLE);
        assertThat(assessment.businessAcceptanceDecision()).isEqualTo(BusinessAcceptanceDecision.ACCEPTABLE);
        assertThat(assessment.matchBases()).contains(MatchBasis.DISTRIBUTOR_NAME);
    }

    @Test
    void distributionAgreementDoesNotAcceptAccountOnlyBankName() {
        var assessment = evaluate(DocumentType.DISTRIBUTION_AGREEMENT, null, null, List.of(
                page(1, "收款账户信息：开户行：示例机构。账号：123456。")));

        assertThat(assessment.decision()).isEqualTo(TargetMatchDecision.INSUFFICIENT_EVIDENCE);
    }

    @Test
    void riskDisclosureAllowsFamilyMatchWithoutCode() {
        Product product = repository.findByCode("XYWTL001A").orElseThrow();
        var assessment = evaluate(DocumentType.RISK_DISCLOSURE, "XYWTL001A", product, List.of(
                page(1, "示例理财丁【稳添利最短持有期日开固收类】理财产品风险揭示书")));

        assertThat(assessment.decision()).isEqualTo(TargetMatchDecision.MATCH_BY_PRODUCT_FAMILY);
        assertThat(assessment.businessAcceptanceDecision()).isEqualTo(BusinessAcceptanceDecision.ACCEPTABLE_WITH_WARNING);
    }

    @Test
    void periodicAnnouncementContainsTargetAmongManyProducts() {
        var assessment = evaluate(DocumentType.PERIODIC_ANNOUNCEMENT, "ZYBSG0056D", targetProduct, List.of(
                page(1, "产品代码：AAA00001\n产品代码：ZYBSG0056D\n产品代码：BBB00002\n产品代码：CCC00003\n产品代码：DDD00004")));

        assertThat(assessment.documentScope()).isEqualTo(DocumentScope.MULTI_PRODUCT);
        assertThat(assessment.decision()).isEqualTo(TargetMatchDecision.CONTAINED);
    }

    @Test
    void maturityAnnouncementAllowsMultipleShares() {
        var assessment = evaluate(DocumentType.MATURITY_ANNOUNCEMENT, "ZYBSG0056D", targetProduct, List.of(
                page(1, "示例理财乙琮简宝石59号理财产品到期公告\nZYBSG0056A份额：1.0093\nZYBSG0056B份额：1.0096\nZYBSG0056D份额：1.0096")));

        assertThat(assessment.documentScope()).isEqualTo(DocumentScope.MULTI_SHARE);
        assertThat(assessment.decision()).isEqualTo(TargetMatchDecision.MATCH);
        assertThat(assessment.matchBases()).contains(MatchBasis.SHARE_CODE);
    }

    @Test
    void navAnnouncementExtractsTargetTableRow() {
        List<DocumentPage> pages = List.of(page(1, """
                示例理财乙琮融九溪添利270天持有2号双利增强理财产品I份额
                Z7011826000195
                ZYJXG00600
                ZYJXG0060I
                20260721
                1.0037
                """));
        Product product = new Product(
                "ZYJXG0060I",
                "示例理财乙琮融九溪添利270天持有2号双利增强理财产品I份额",
                List.of(),
                "示例理财管理机构乙",
                "示例理财管理机构乙",
                "ZYJXG0060",
                List.of("ZYJXG0060I"),
                List.of("ZYJXG00600"),
                List.of("示例理财乙琮融九溪添利270天持有2号"),
                List.of(),
                "理财产品");
        repository.add(product);

        var assessment = evaluate(DocumentType.NAV_ANNOUNCEMENT, "ZYJXG0060I", product, pages);
        List<ProductTableRow> rows = tableRowExtractor.extractTargetRows(pages, "ZYJXG0060I", product);

        assertThat(assessment.decision()).isEqualTo(TargetMatchDecision.CONTAINED);
        assertThat(assessment.matchBases()).contains(MatchBasis.TABLE_ROW_EVIDENCE);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).salesCode()).isEqualTo("ZYJXG0060I");
    }

    private TargetProductAssessment evaluate(DocumentType type,
                                             String declaredCode,
                                             Product product,
                                             List<DocumentPage> pages) {
        RuleReviewService.RuleReviewOutcome outcome = ruleService.review(pages, type, declaredCode, product);
        ReviewContext context = new ReviewContext(pages, "test.pdf", DocumentCategory.AUTO,
                declaredCode, type.displayName(), type, type, null, product,
                List.of("示例机构", "示例机构股份有限公司"));
        return registry.select(type, type).evaluate(context, outcome, null);
    }

    private DocumentPage page(int pageNumber, String text) {
        return new DocumentPage(pageNumber, text, text);
    }
}
