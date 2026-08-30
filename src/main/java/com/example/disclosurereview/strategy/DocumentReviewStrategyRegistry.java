package com.example.disclosurereview.strategy;

import com.example.disclosurereview.config.ReviewProperties;
import com.example.disclosurereview.model.BusinessAcceptanceDecision;
import com.example.disclosurereview.model.DocumentPage;
import com.example.disclosurereview.model.DocumentScope;
import com.example.disclosurereview.model.DocumentType;
import com.example.disclosurereview.model.Evidence;
import com.example.disclosurereview.model.EvidenceValue;
import com.example.disclosurereview.model.LlmReviewResult;
import com.example.disclosurereview.model.MatchBasis;
import com.example.disclosurereview.model.Product;
import com.example.disclosurereview.model.ProductIdentityDecision;
import com.example.disclosurereview.model.TargetMatchDecision;
import com.example.disclosurereview.model.TargetProductAssessment;
import com.example.disclosurereview.rule.RuleReviewService;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 文件类型策略注册器。声明类型优先，正文候选类型兜底，UNKNOWN 使用保守策略。 */
@Component
public class DocumentReviewStrategyRegistry {

    private final ReviewProperties reviewProperties;
    private final ProductCodeFamilyResolver familyResolver;
    private final ProductNameMatcher nameMatcher;
    private final ProductSeriesExtractor seriesExtractor;
    private final InstitutionRoleExtractor roleExtractor;

    public DocumentReviewStrategyRegistry(ReviewProperties reviewProperties,
                                          ProductCodeFamilyResolver familyResolver,
                                          ProductNameMatcher nameMatcher,
                                          ProductSeriesExtractor seriesExtractor,
                                          InstitutionRoleExtractor roleExtractor) {
        this.reviewProperties = reviewProperties;
        this.familyResolver = familyResolver;
        this.nameMatcher = nameMatcher;
        this.seriesExtractor = seriesExtractor;
        this.roleExtractor = roleExtractor;
    }

    public static DocumentReviewStrategyRegistry defaultRegistry() {
        ReviewProperties props = new ReviewProperties();
        ProductNameNormalizer normalizer = new ProductNameNormalizer();
        ProductSeriesExtractor seriesExtractor = new ProductSeriesExtractor(normalizer);
        ProductCodeFamilyResolver familyResolver = new ProductCodeFamilyResolver();
        ProductNameMatcher matcher = new ProductNameMatcher(normalizer, seriesExtractor);
        InstitutionRoleExtractor roleExtractor = new InstitutionRoleExtractor();
        return new DocumentReviewStrategyRegistry(props, familyResolver, matcher, seriesExtractor, roleExtractor);
    }

    public DocumentReviewStrategy select(DocumentType declaredType, DocumentType candidateType) {
        DocumentType selected = declaredType != null && declaredType != DocumentType.UNKNOWN
                ? declaredType
                : candidateType;
        if (selected == null || selected == DocumentType.UNKNOWN) {
            selected = DocumentType.UNKNOWN;
        }
        return new HeuristicDocumentReviewStrategy(selected, reviewProperties, familyResolver,
                nameMatcher, seriesExtractor, roleExtractor);
    }

    public List<DocumentType> supportedTypes() {
        return DocumentType.supportedTypes();
    }

    private static class HeuristicDocumentReviewStrategy implements DocumentReviewStrategy {

        private final DocumentType documentType;
        private final ReviewProperties reviewProperties;
        private final ProductCodeFamilyResolver familyResolver;
        private final ProductNameMatcher nameMatcher;
        private final ProductSeriesExtractor seriesExtractor;
        private final InstitutionRoleExtractor roleExtractor;

        HeuristicDocumentReviewStrategy(DocumentType documentType,
                                        ReviewProperties reviewProperties,
                                        ProductCodeFamilyResolver familyResolver,
                                        ProductNameMatcher nameMatcher,
                                        ProductSeriesExtractor seriesExtractor,
                                        InstitutionRoleExtractor roleExtractor) {
            this.documentType = documentType;
            this.reviewProperties = reviewProperties;
            this.familyResolver = familyResolver;
            this.nameMatcher = nameMatcher;
            this.seriesExtractor = seriesExtractor;
            this.roleExtractor = roleExtractor;
        }

        @Override
        public boolean supports(DocumentType type) {
            return documentType == type;
        }

        @Override
        public StrategyReviewPolicy buildPolicy(ReviewContext context) {
            DocumentScope scope = expectedScope(documentType);
            boolean multi = documentType.allowsMultipleProducts();
            boolean concreteRequired = documentType == DocumentType.PRODUCT_DESCRIPTION
                    || documentType == DocumentType.INVESTMENT_AGREEMENT
                    || documentType == DocumentType.ISSUANCE_ANNOUNCEMENT;
            return new StrategyReviewPolicy(documentType, scope, concreteRequired, multi, policyText(documentType));
        }

        @Override
        public TargetProductAssessment evaluate(ReviewContext context,
                                                RuleReviewService.RuleReviewOutcome ruleResult,
                                                LlmReviewResult llmResult) {
            TargetProductAssessment ruleAssessment = evaluateByRule(context, ruleResult);
            TargetProductAssessment llmAssessment = llmResult == null ? null : llmResult.targetProductAssessment();
            if (usable(llmAssessment) && strongerThan(llmAssessment, ruleAssessment)) {
                return llmAssessment;
            }
            return ruleAssessment;
        }

        private TargetProductAssessment evaluateByRule(ReviewContext context,
                                                       RuleReviewService.RuleReviewOutcome ruleResult) {
            return switch (documentType) {
                case DISTRIBUTION_AGREEMENT -> evaluateDistribution(context, ruleResult);
                case RISK_DISCLOSURE, CUSTOMER_RIGHTS_NOTICE -> evaluateFamilyDocument(context, ruleResult);
                case PERIODIC_ANNOUNCEMENT -> evaluateMultiProduct(context, ruleResult, DocumentScope.MULTI_PRODUCT);
                case MATURITY_ANNOUNCEMENT -> evaluateMaturity(context, ruleResult);
                case NAV_ANNOUNCEMENT -> evaluateNav(context, ruleResult);
                case OTHER_ANNOUNCEMENT -> evaluateOther(context, ruleResult);
                case PRODUCT_DESCRIPTION, INVESTMENT_AGREEMENT, ISSUANCE_ANNOUNCEMENT ->
                        evaluateSingleProduct(context, ruleResult, expectedScope(documentType));
                case UNKNOWN -> evaluateUnknown(context, ruleResult);
            };
        }

        private TargetProductAssessment evaluateDistribution(ReviewContext context,
                                                            RuleReviewService.RuleReviewOutcome ruleResult) {
            var agency = roleExtractor.assess(context.pages(), targetBankNames(context), true);
            if (agency.targetBankIsDistributor()) {
                return assessment(
                        TargetMatchDecision.ACCEPTABLE_BY_DISTRIBUTOR,
                        ProductIdentityDecision.PRODUCT_NOT_APPLICABLE,
                        BusinessAcceptanceDecision.ACCEPTABLE,
                        DocumentScope.GENERAL_AGREEMENT,
                        List.of(MatchBasis.DISTRIBUTOR_NAME),
                        context,
                        null,
                        null,
                        null,
                        agency.institutionName(),
                        agency.evidence(),
                        0.9,
                        "正文未识别到具体产品代码或完整产品名称，但已确认目标机构作为代理销售方，依据当前业务规则判定为可接受。",
                        "建议人工确认协议双方角色和适用范围。");
            }
            TargetProductAssessment productMatch = evaluateSingleProduct(context, ruleResult, DocumentScope.GENERAL_AGREEMENT);
            if (productMatch.decision() == TargetMatchDecision.MATCH) {
                return productMatch;
            }
            return assessment(
                    TargetMatchDecision.INSUFFICIENT_EVIDENCE,
                    ProductIdentityDecision.PRODUCT_NOT_IDENTIFIED,
                    BusinessAcceptanceDecision.MANUAL_REVIEW,
                    DocumentScope.GENERAL_AGREEMENT,
                    List.of(),
                    context,
                    null,
                    null,
                    null,
                    null,
                    List.of(),
                    0.35,
                    "代销协议未确认目标机构的代理销售方、代销机构或销售机构角色。",
                    "请人工核查协议正文核心条款，避免仅凭页眉、地址或账户信息判断。");
        }

        private TargetProductAssessment evaluateFamilyDocument(ReviewContext context,
                                                              RuleReviewService.RuleReviewOutcome ruleResult) {
            TargetProductAssessment exact = evaluateExactCode(context, ruleResult, DocumentScope.PRODUCT_FAMILY,
                    TargetMatchDecision.MATCH, List.of(MatchBasis.EXACT_PRODUCT_CODE));
            if (exact != null) {
                return exact;
            }
            NameEvidence nameEvidence = findNameEvidence(context);
            if (nameEvidence.confidence() >= 0.55) {
                TargetMatchDecision decision = nameEvidence.bases().contains(MatchBasis.PRODUCT_SERIES_NAME)
                        ? TargetMatchDecision.MATCH_BY_PRODUCT_FAMILY
                        : TargetMatchDecision.POSSIBLE_MATCH;
                return assessment(
                        decision,
                        TargetProductAssessment.identityFrom(decision),
                        TargetProductAssessment.acceptanceFrom(decision),
                        DocumentScope.PRODUCT_FAMILY,
                        nameEvidence.bases(),
                        context,
                        null,
                        context.targetProduct() == null ? null : context.targetProduct().productName(),
                        firstSeries(context),
                        matchedInstitution(context, nameEvidence.bases()),
                        nameEvidence.evidence(),
                        nameEvidence.confidence(),
                        "当前文件允许通过产品系列、管理人或名称简称建立目标产品关系，未因产品代码缺失自动判定异常。",
                        "建议人工复核系列名称与目标产品主数据是否属于同一产品族。");
            }
            return insufficient(context, DocumentScope.PRODUCT_FAMILY,
                    "未找到足够的产品代码、系列名称或管理人证据。");
        }

        private TargetProductAssessment evaluateMultiProduct(ReviewContext context,
                                                            RuleReviewService.RuleReviewOutcome ruleResult,
                                                            DocumentScope scope) {
            TargetProductAssessment exact = evaluateExactCode(context, ruleResult, scope,
                    TargetMatchDecision.CONTAINED,
                    List.of(MatchBasis.EXACT_PRODUCT_CODE, MatchBasis.TABLE_ROW_EVIDENCE));
            if (exact != null) {
                return exact;
            }
            NameEvidence nameEvidence = findNameEvidence(context);
            if (nameEvidence.confidence() >= 0.55) {
                return assessment(
                        TargetMatchDecision.CONTAINED,
                        ProductIdentityDecision.PRODUCT_CONTAINED,
                        BusinessAcceptanceDecision.ACCEPTABLE,
                        scope,
                        merge(nameEvidence.bases(), List.of(MatchBasis.PRODUCT_NAME_SEMANTIC)),
                        context,
                        null,
                        context.targetProduct() == null ? null : context.targetProduct().productName(),
                        firstSeries(context),
                        matchedInstitution(context, nameEvidence.bases()),
                        nameEvidence.evidence(),
                        Math.max(0.72, nameEvidence.confidence()),
                        "多产品公告中识别到目标产品代码、名称、系列或表格上下文，其他产品默认为共同披露对象。",
                        "通常无需因其他产品同时出现而生成代码冲突；请仅复核目标产品是否完整列示。");
            }
            if (!codes(ruleResult).isEmpty()) {
                return assessment(
                        TargetMatchDecision.MISMATCH,
                        ProductIdentityDecision.PRODUCT_MISMATCH,
                        BusinessAcceptanceDecision.REJECT_SUGGESTED,
                        scope,
                        List.of(),
                        context,
                        null,
                        null,
                        null,
                        null,
                        firstCodeEvidence(ruleResult),
                        0.72,
                        "正文列出了其他产品，但未发现声明目标产品的代码、名称、系列或表格记录。",
                        "建议人工核查目标产品是否遗漏或文件名声明是否错误。");
            }
            return insufficient(context, scope, "正文未提供足够的目标产品或多产品表格证据。");
        }

        private TargetProductAssessment evaluateMaturity(ReviewContext context,
                                                        RuleReviewService.RuleReviewOutcome ruleResult) {
            TargetProductAssessment exact = evaluateExactCode(context, ruleResult, DocumentScope.MULTI_SHARE,
                    TargetMatchDecision.MATCH,
                    List.of(MatchBasis.SHARE_CODE, MatchBasis.EXACT_PRODUCT_CODE));
            if (exact != null) {
                return exact;
            }
            for (String code : codes(ruleResult)) {
                if (familyResolver.isSameFamily(code, context.declaredProductCode(), context.targetProduct())) {
                    EvidenceValue ev = evidenceFor(ruleResult, code);
                    return assessment(
                            TargetMatchDecision.MATCH_BY_PRODUCT_FAMILY,
                            ProductIdentityDecision.PRODUCT_FAMILY_MATCHED,
                            BusinessAcceptanceDecision.ACCEPTABLE_WITH_WARNING,
                            DocumentScope.MULTI_SHARE,
                            List.of(MatchBasis.PRODUCT_CODE_FAMILY, MatchBasis.SHARE_CODE),
                            context,
                            code,
                            context.targetProduct() == null ? null : context.targetProduct().productName(),
                            firstSeries(context),
                            null,
                            evidenceList(ev),
                            0.62,
                            "到期公告中识别到同一稳定前缀或产品族的份额代码，未将多个份额自动判定为冲突。",
                            "当前为低置信度份额关系，建议结合产品库 parentProductCode/shareCodes 人工确认。");
                }
            }
            return evaluateMultiProduct(context, ruleResult, DocumentScope.MULTI_SHARE);
        }

        private TargetProductAssessment evaluateNav(ReviewContext context,
                                                   RuleReviewService.RuleReviewOutcome ruleResult) {
            TargetProductAssessment exact = evaluateExactCode(context, ruleResult, DocumentScope.MULTI_PRODUCT,
                    TargetMatchDecision.CONTAINED,
                    List.of(MatchBasis.PRODUCT_CODE_IN_TABLE, MatchBasis.TABLE_ROW_EVIDENCE));
            if (exact != null) {
                return exact;
            }
            return evaluateMultiProduct(context, ruleResult, DocumentScope.MULTI_PRODUCT);
        }

        private TargetProductAssessment evaluateOther(ReviewContext context,
                                                     RuleReviewService.RuleReviewOutcome ruleResult) {
            if (codes(ruleResult).size() > 1) {
                return evaluateMultiProduct(context, ruleResult, DocumentScope.MULTI_PRODUCT);
            }
            return evaluateSingleProduct(context, ruleResult, DocumentScope.SINGLE_PRODUCT);
        }

        private TargetProductAssessment evaluateUnknown(ReviewContext context,
                                                       RuleReviewService.RuleReviewOutcome ruleResult) {
            TargetProductAssessment exact = evaluateExactCode(context, ruleResult, DocumentScope.UNKNOWN,
                    TargetMatchDecision.MATCH, List.of(MatchBasis.EXACT_PRODUCT_CODE));
            if (exact != null) {
                return exact;
            }
            NameEvidence nameEvidence = findNameEvidence(context);
            if (nameEvidence.confidence() >= 0.55) {
                return assessment(TargetMatchDecision.POSSIBLE_MATCH,
                        ProductIdentityDecision.PRODUCT_POSSIBLY_MATCHED,
                        BusinessAcceptanceDecision.MANUAL_REVIEW,
                        DocumentScope.UNKNOWN,
                        nameEvidence.bases(),
                        context,
                        null,
                        context.targetProduct() == null ? null : context.targetProduct().productName(),
                        firstSeries(context),
                        matchedInstitution(context, nameEvidence.bases()),
                        nameEvidence.evidence(),
                        nameEvidence.confidence(),
                        "文件类型未知，但正文存在与目标产品相关的名称、系列或机构证据。",
                        "建议先人工确认文件类型，再判断产品一致性。");
            }
            return insufficient(context, DocumentScope.UNKNOWN, "无法判断文件类型和目标产品证据。");
        }

        private TargetProductAssessment evaluateSingleProduct(ReviewContext context,
                                                             RuleReviewService.RuleReviewOutcome ruleResult,
                                                             DocumentScope scope) {
            TargetProductAssessment exact = evaluateExactCode(context, ruleResult, scope,
                    TargetMatchDecision.MATCH, List.of(MatchBasis.EXACT_PRODUCT_CODE));
            if (exact != null) {
                return exact;
            }
            NameEvidence nameEvidence = findNameEvidence(context);
            if (nameEvidence.confidence() >= 0.55) {
                TargetMatchDecision decision = nameEvidence.confidence() >= 0.78
                        ? TargetMatchDecision.MATCH
                        : TargetMatchDecision.POSSIBLE_MATCH;
                return assessment(
                        decision,
                        TargetProductAssessment.identityFrom(decision),
                        TargetProductAssessment.acceptanceFrom(decision),
                        scope,
                        merge(nameEvidence.bases(), List.of(MatchBasis.SECTION_EVIDENCE)),
                        context,
                        null,
                        context.targetProduct() == null ? null : context.targetProduct().productName(),
                        firstSeries(context),
                        matchedInstitution(context, nameEvidence.bases()),
                        nameEvidence.evidence(),
                        nameEvidence.confidence(),
                        "全文范围内识别到目标产品名称、别名、系列或管理人证据，不再仅依赖首页主要产品字段。",
                        "若缺少产品代码，请人工复核名称简称和产品库别名是否维护充分。");
            }
            if (!codes(ruleResult).isEmpty() && StringUtils.hasText(context.declaredProductCode())) {
                return assessment(
                        TargetMatchDecision.MISMATCH,
                        ProductIdentityDecision.PRODUCT_MISMATCH,
                        BusinessAcceptanceDecision.REJECT_SUGGESTED,
                        scope,
                        List.of(),
                        context,
                        null,
                        null,
                        null,
                        null,
                        firstCodeEvidence(ruleResult),
                        0.75,
                        "单产品文件正文出现产品代码，但未发现声明目标产品代码或名称证据。",
                        "建议人工核查是否存在文件名声明错误或核心产品信息模板残留。");
            }
            return insufficient(context, scope, "未找到足够的目标产品代码或名称证据。");
        }

        private TargetProductAssessment evaluateExactCode(ReviewContext context,
                                                         RuleReviewService.RuleReviewOutcome ruleResult,
                                                         DocumentScope scope,
                                                         TargetMatchDecision decision,
                                                         List<MatchBasis> baseBases) {
            for (String code : familyResolver.targetCodes(context.declaredProductCode(), context.targetProduct())) {
                EvidenceValue ev = evidenceFor(ruleResult, code);
                if (ev != null || contains(context.pages(), code)) {
                    List<Evidence> evidence = ev == null ? findEvidence(context.pages(), code) : evidenceList(ev);
                    return assessment(
                            decision,
                            TargetProductAssessment.identityFrom(decision),
                            TargetProductAssessment.acceptanceFrom(decision),
                            scope,
                            baseBases,
                            context,
                            code,
                            context.targetProduct() == null ? null : context.targetProduct().productName(),
                            firstSeries(context),
                            context.targetProduct() == null ? null : firstText(context.targetProduct().managerName(), context.targetProduct().issuerName()),
                            evidence,
                            0.92,
                            "正文中精确出现声明目标产品代码、份额代码或产品库维护的代码别名。",
                            "");
                }
            }
            return null;
        }

        private TargetProductAssessment insufficient(ReviewContext context,
                                                     DocumentScope scope,
                                                     String explanation) {
            return assessment(TargetMatchDecision.INSUFFICIENT_EVIDENCE,
                    ProductIdentityDecision.PRODUCT_NOT_IDENTIFIED,
                    BusinessAcceptanceDecision.MANUAL_REVIEW,
                    scope,
                    List.of(),
                    context,
                    null,
                    null,
                    null,
                    null,
                    List.of(),
                    0.35,
                    explanation,
                    "建议人工复核文件名、B9、正文标题和产品库主数据。");
        }

        private TargetProductAssessment assessment(TargetMatchDecision decision,
                                                  ProductIdentityDecision identityDecision,
                                                  BusinessAcceptanceDecision businessDecision,
                                                  DocumentScope scope,
                                                  List<MatchBasis> bases,
                                                  ReviewContext context,
                                                  String matchedCode,
                                                  String matchedName,
                                                  String matchedSeries,
                                                  String matchedInstitution,
                                                  List<Evidence> evidence,
                                                  double confidence,
                                                  String explanation,
                                                  String suggestion) {
            return new TargetProductAssessment(decision, identityDecision, businessDecision,
                    scope, bases, context.declaredProductCode(), matchedCode, matchedName,
                    matchedSeries, matchedInstitution, evidence, confidence, explanation, suggestion);
        }

        private NameEvidence findNameEvidence(ReviewContext context) {
            ProductNameMatcher.MatchResult match = nameMatcher.match(context.targetProduct(), context.fullText(), null);
            List<Evidence> evidence = new ArrayList<>();
            if (context.targetProduct() != null) {
                evidence.addAll(findEvidence(context.pages(), context.targetProduct().productName()));
                for (String alias : context.targetProduct().safeAliases()) {
                    if (evidence.isEmpty()) {
                        evidence.addAll(findEvidence(context.pages(), alias));
                    }
                }
                for (String series : seriesExtractor.seriesCandidates(context.targetProduct(), context.fileName())) {
                    if (evidence.isEmpty()) {
                        evidence.addAll(findEvidence(context.pages(), series));
                    }
                }
                if (evidence.isEmpty()) {
                    evidence.addAll(findFirstSeriesEvidence(context.pages()));
                }
                if (evidence.isEmpty()) {
                    evidence.addAll(findEvidence(context.pages(), context.targetProduct().managerName()));
                }
                if (evidence.isEmpty()) {
                    evidence.addAll(findEvidence(context.pages(), context.targetProduct().issuerName()));
                }
            }
            return new NameEvidence(match.bases(), evidence, match.confidence());
        }

        private List<Evidence> findFirstSeriesEvidence(List<DocumentPage> pages) {
            if (pages == null) {
                return List.of();
            }
            for (DocumentPage page : pages) {
                for (String series : seriesExtractor.seriesFromText(page.normalizedText())) {
                    List<Evidence> evidence = findEvidence(List.of(page), series);
                    if (!evidence.isEmpty()) {
                        return evidence;
                    }
                }
            }
            return List.of();
        }

        private boolean strongerThan(TargetProductAssessment candidate, TargetProductAssessment current) {
            if (current == null) {
                return true;
            }
            double cc = candidate.confidence() == null ? 0.0 : candidate.confidence();
            double rc = current.confidence() == null ? 0.0 : current.confidence();
            if (candidate.decision() == TargetMatchDecision.MISMATCH && cc >= 0.75) {
                return true;
            }
            return cc > rc && !candidate.evidence().isEmpty();
        }

        private boolean usable(TargetProductAssessment assessment) {
            return assessment != null
                    && assessment.decision() != null
                    && assessment.decision() != TargetMatchDecision.UNKNOWN;
        }

        private List<String> codes(RuleReviewService.RuleReviewOutcome ruleResult) {
            if (ruleResult == null || ruleResult.productCodeCandidates() == null) {
                return List.of();
            }
            return ruleResult.productCodeCandidates().stream()
                    .map(EvidenceValue::value)
                    .filter(StringUtils::hasText)
                    .distinct()
                    .toList();
        }

        private EvidenceValue evidenceFor(RuleReviewService.RuleReviewOutcome ruleResult, String code) {
            if (!StringUtils.hasText(code) || ruleResult == null || ruleResult.productCodeCandidates() == null) {
                return null;
            }
            return ruleResult.productCodeCandidates().stream()
                    .filter(e -> code.equals(e.value()))
                    .findFirst()
                    .orElse(null);
        }

        private List<Evidence> firstCodeEvidence(RuleReviewService.RuleReviewOutcome ruleResult) {
            if (ruleResult == null || ruleResult.productCodeCandidates() == null || ruleResult.productCodeCandidates().isEmpty()) {
                return List.of();
            }
            return evidenceList(ruleResult.productCodeCandidates().get(0));
        }

        private List<Evidence> evidenceList(EvidenceValue value) {
            if (value == null) {
                return List.of();
            }
            return List.of(new Evidence(value.pageNumber(), value.evidenceText(), true));
        }

        private List<Evidence> findEvidence(List<DocumentPage> pages, String term) {
            if (!StringUtils.hasText(term) || pages == null) {
                return List.of();
            }
            for (DocumentPage page : pages) {
                String text = page.normalizedText();
                if (StringUtils.hasText(text)) {
                    int idx = text.indexOf(term);
                    if (idx >= 0) {
                        return List.of(new Evidence(page.pageNumber(), context(text, idx, idx + term.length()), true));
                    }
                }
            }
            return List.of();
        }

        private boolean contains(List<DocumentPage> pages, String term) {
            return !findEvidence(pages, term).isEmpty();
        }

        private String context(String text, int start, int end) {
            int from = Math.max(0, start - 60);
            int to = Math.min(text.length(), end + 60);
            return text.substring(from, to).replaceAll("\\s+", " ").strip();
        }

        private List<MatchBasis> merge(List<MatchBasis> a, List<MatchBasis> b) {
            Set<MatchBasis> result = new LinkedHashSet<>();
            if (a != null) {
                result.addAll(a);
            }
            if (b != null) {
                result.addAll(b);
            }
            return result.stream().toList();
        }

        private String firstSeries(ReviewContext context) {
            Product product = context.targetProduct();
            if (product == null) {
                return null;
            }
            if (!product.safeSeriesNames().isEmpty()) {
                return product.safeSeriesNames().get(0);
            }
            List<String> candidates = seriesExtractor.seriesCandidates(product, context.fileName());
            return candidates.isEmpty() ? null : candidates.get(0);
        }

        private String matchedInstitution(ReviewContext context, List<MatchBasis> bases) {
            Product product = context.targetProduct();
            if (product == null || bases == null) {
                return null;
            }
            if (bases.contains(MatchBasis.MANAGER_NAME) && StringUtils.hasText(product.managerName())) {
                return product.managerName();
            }
            if (bases.contains(MatchBasis.ISSUER_NAME) && StringUtils.hasText(product.issuerName())) {
                return product.issuerName();
            }
            return firstText(product.managerName(), product.issuerName());
        }

        private String firstText(String first, String second) {
            return StringUtils.hasText(first) ? first : second;
        }

        private List<String> targetBankNames(ReviewContext context) {
            if (context.targetBankNames() != null && !context.targetBankNames().isEmpty()) {
                return context.targetBankNames();
            }
            return reviewProperties.getInstitution().getTargetBankNames();
        }

        private DocumentScope expectedScope(DocumentType type) {
            return switch (type) {
                case PRODUCT_DESCRIPTION, INVESTMENT_AGREEMENT, ISSUANCE_ANNOUNCEMENT -> DocumentScope.SINGLE_PRODUCT;
                case RISK_DISCLOSURE, CUSTOMER_RIGHTS_NOTICE -> DocumentScope.PRODUCT_FAMILY;
                case DISTRIBUTION_AGREEMENT -> DocumentScope.GENERAL_AGREEMENT;
                case MATURITY_ANNOUNCEMENT -> DocumentScope.MULTI_SHARE;
                case PERIODIC_ANNOUNCEMENT, NAV_ANNOUNCEMENT -> DocumentScope.MULTI_PRODUCT;
                case OTHER_ANNOUNCEMENT, UNKNOWN -> DocumentScope.UNKNOWN;
            };
        }

        private String policyText(DocumentType type) {
            return switch (type) {
                case PRODUCT_DESCRIPTION -> "当前文件为产品说明书。请全文搜索产品代码和产品名称，重点关注产品基本情况、产品基本信息、产品概况、产品要素、理财产品基本情况、产品名称、产品代码、登记编码等章节，不得只看首页。";
                case INVESTMENT_AGREEMENT -> "当前文件为投资协议书。请重点检查产品基本信息、投资标的、认购信息、申购信息、投资者确认、交易申请和签署条款中的产品代码、名称或系列。";
                case DISTRIBUTION_AGREEMENT -> "当前文件疑似为代销协议。具体产品代码和产品名称不是必填审核条件；请判断目标机构是否作为代理销售方、代销机构、销售机构或承担销售职责的一方出现，且证据来自核心协议条款。";
                case RISK_DISCLOSURE -> "当前文件为风险揭示书。产品代码可选，标题、风险提示段落和投资者确认部分中的产品系列、管理人和名称核心词均可作为证据；不要把代码缺失直接作为高风险。";
                case CUSTOMER_RIGHTS_NOTICE -> "当前文件为客户权益须知或投资者权益须知。产品代码可选，请提取标题括号或书名号中的产品系列、管理人和产品类型，支持系列匹配。";
                case ISSUANCE_ANNOUNCEMENT -> "当前文件为发行或成立公告，通常对应单一产品。产品代码和产品名称是强证据；若代码缺失但名称明确，可返回可能匹配并建议复核。";
                case PERIODIC_ANNOUNCEMENT -> "当前文件为定期公告，可能同时披露多个产品。审核目标是判断声明目标产品是否被包含，其他产品属于正常共同披露对象。";
                case MATURITY_ANNOUNCEMENT -> "当前文件为到期或兑付公告，可能包含同一母产品的多个份额代码。请判断目标份额代码是否出现，A/B/D 等份额不得自动视为冲突。";
                case NAV_ANNOUNCEMENT -> "当前文件为净值公告，通常是多产品汇总表格。请定位目标产品对应的表格行，区分登记编码、产品代码和销售代码，其他产品不得自动认定为冲突。";
                case OTHER_ANNOUNCEMENT -> "当前文件为其他公告。请先判断是单产品还是多产品场景；单产品做普通一致性审核，多产品做目标产品包含关系审核。";
                case UNKNOWN -> "当前文件类型无法确认。请保守判断目标产品是否被正文支持，证据不足时返回人工复核。";
            };
        }

        private record NameEvidence(List<MatchBasis> bases, List<Evidence> evidence, double confidence) {
            private NameEvidence {
                bases = bases == null ? List.of() : List.copyOf(bases);
                evidence = evidence == null ? List.of() : List.copyOf(evidence);
            }
        }
    }
}
