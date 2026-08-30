package com.example.disclosurereview.rule;

import com.example.disclosurereview.model.DocumentPage;
import com.example.disclosurereview.model.DocumentType;
import com.example.disclosurereview.model.EvidenceValue;
import com.example.disclosurereview.model.IssueType;
import com.example.disclosurereview.model.Product;
import com.example.disclosurereview.model.ReviewIssue;
import com.example.disclosurereview.model.Severity;
import com.example.disclosurereview.repository.ProductRepository;
import com.example.disclosurereview.strategy.ProductCodeFamilyResolver;
import com.example.disclosurereview.util.TextNormalizer;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 规则审核：确定性任务优先由规则完成。
 * 1. 从正文提取候选产品代码（标签模式 + 产品库精确命中）；
 * 2. 多产品代码冲突候选检测（交由大模型进一步判断，不直接认定错误）。
 */
@Service
public class RuleReviewService {

    public static final String RULE_PRODUCT_CODE_EXTRACTION = "PRODUCT_CODE_EXTRACTION";
    public static final String RULE_PRODUCT_NAME_EXTRACTION = "PRODUCT_NAME_EXTRACTION";
    public static final String RULE_DECLARED_PRODUCT_NOT_FOUND = "DECLARED_PRODUCT_NOT_FOUND";
    public static final String RULE_CONTENT_LOGIC_CONFLICT = "CONTENT_LOGIC_CONFLICT";
    public static final String RULE_CONTENT_PRODUCT_CODE_CONFLICT = "CONTENT_PRODUCT_CODE_CONFLICT";
    public static final String RULE_POSSIBLE_TEMPLATE_RESIDUE = "POSSIBLE_TEMPLATE_RESIDUE";

    /** “产品代码：XXXX” / “产品编号：XXXX” / “产品代码 XXXX” */
    private static final Pattern LABEL_PATTERN = Pattern.compile(
            "产品(?:代码|编号)\\s*[:：]?\\s*([A-Za-z0-9][A-Za-z0-9\\-]{3,19})");
    private static final Pattern RISK_SCALE_HEADER_PATTERN = Pattern.compile(
            "(?:风险程度|风险等级)[^.;]{0,80}(?:从低到高|由低到高)(?:分为|包括)五级");
    private static final Pattern RISK_LEVEL_ENTRY_PATTERN = Pattern.compile(
            "(低风险|中低风险|中风险|中高风险|高风险)产品?\\(R([1-5])\\)");

    private static final int CONTEXT_RADIUS = 30;

    private final ProductRepository productRepository;
    private final ProductCodeFamilyResolver familyResolver;

    public RuleReviewService(ProductRepository productRepository,
                             ProductCodeFamilyResolver familyResolver) {
        this.productRepository = productRepository;
        this.familyResolver = familyResolver;
    }

    public RuleReviewOutcome review(List<DocumentPage> pages) {
        return review(pages, DocumentType.UNKNOWN, null, null);
    }

    public RuleReviewOutcome review(List<DocumentPage> pages,
                                    DocumentType documentType,
                                    String declaredProductCode,
                                    Product targetProduct) {
        return review(pages, documentType, declaredProductCode, targetProduct, Set.of());
    }

    public RuleReviewOutcome review(List<DocumentPage> pages,
                                    DocumentType documentType,
                                    String declaredProductCode,
                                    Product targetProduct,
                                    Set<String> enabledRuleCodes) {
        List<EvidenceValue> codeCandidates = isEnabled(enabledRuleCodes, RULE_PRODUCT_CODE_EXTRACTION)
                ? extractProductCodeCandidates(pages)
                : List.of();
        List<EvidenceValue> nameCandidates = isEnabled(enabledRuleCodes, RULE_PRODUCT_NAME_EXTRACTION)
                ? extractProductNameCandidates(pages)
                : List.of();
        List<ReviewIssue> issues = new ArrayList<>();

        if (isEnabled(enabledRuleCodes, RULE_DECLARED_PRODUCT_NOT_FOUND)
                && StringUtils.hasText(declaredProductCode) && targetProduct == null) {
            String declared = declaredProductCode.strip();
            issues.add(new ReviewIssue(
                    IssueType.DECLARED_PRODUCT_NOT_FOUND,
                    Severity.MEDIUM,
                    1.0,
                    null,
                    "声明产品代码：" + declared,
                    "声明产品代码未在当前模拟产品库中找到，系统无法基于产品主数据确认目标产品。",
                    "请确认文件名或外部传入的产品代码是否正确，或先补充对应产品库记录。",
                    "RULE",
                    true));
        }

        if (isEnabled(enabledRuleCodes, RULE_CONTENT_LOGIC_CONFLICT)) {
            detectInternalLogicConflicts(pages, issues);
        }

        // 多产品公告默认允许出现多个产品代码；仅单产品文件中才生成冲突候选。
        Map<String, EvidenceValue> distinct = new LinkedHashMap<>();
        for (EvidenceValue c : codeCandidates) {
            distinct.putIfAbsent(c.value(), c);
        }
        if (isEnabled(enabledRuleCodes, RULE_CONTENT_PRODUCT_CODE_CONFLICT)
                && shouldRaiseCodeConflict(documentType, declaredProductCode, targetProduct, distinct)) {
            String joined = String.join("、", distinct.keySet());
            EvidenceValue first = codeCandidates.get(0);
            issues.add(new ReviewIssue(
                    IssueType.CONTENT_PRODUCT_CODE_CONFLICT,
                    Severity.MEDIUM,
                    0.5,
                    first.pageNumber(),
                    first.evidenceText(),
                    "正文中出现多个不同产品代码: " + joined + "，可能是正常引用也可能是模板残留，需结合上下文判断",
                    "请人工确认正文是否混用了其他产品的模板内容",
                    "RULE",
                    true));
        }
        if (isEnabled(enabledRuleCodes, RULE_POSSIBLE_TEMPLATE_RESIDUE)) {
            detectCoreTemplateResidue(documentType, declaredProductCode, targetProduct, codeCandidates, issues);
        }

        return new RuleReviewOutcome(codeCandidates, nameCandidates, List.of(), issues);
    }

    private boolean isEnabled(Set<String> enabledRuleCodes, String ruleCode) {
        return enabledRuleCodes == null || enabledRuleCodes.isEmpty() || enabledRuleCodes.contains(ruleCode);
    }

    /** 检测正文中明确的编号、名称和顺序映射是否自相矛盾。 */
    private void detectInternalLogicConflicts(List<DocumentPage> pages, List<ReviewIssue> issues) {
        if (pages == null) {
            return;
        }
        Map<Integer, String> expectedLabels = Map.of(
                1, "低风险",
                2, "中低风险",
                3, "中风险",
                4, "中高风险",
                5, "高风险");
        for (DocumentPage page : pages) {
            String text = page.normalizedText();
            String matchText = TextNormalizer.normalizeForMatch(text);
            Matcher header = RISK_SCALE_HEADER_PATTERN.matcher(matchText);
            while (header.find()) {
                int sentenceEnd = firstSentenceEnd(matchText, header.end());
                String definition = matchText.substring(header.start(), sentenceEnd);
                Matcher entryMatcher = RISK_LEVEL_ENTRY_PATTERN.matcher(definition);
                List<String> mismatches = new ArrayList<>();
                while (entryMatcher.find()) {
                    int level = Integer.parseInt(entryMatcher.group(2));
                    String actual = entryMatcher.group(1);
                    String expected = expectedLabels.get(level);
                    if (expected != null && !expected.equals(actual)) {
                        mismatches.add("R" + level + "为" + actual + "，应为" + expected);
                    }
                }
                if (!mismatches.isEmpty()) {
                    issues.add(new ReviewIssue(
                            IssueType.CONTENT_LOGIC_CONFLICT,
                            Severity.HIGH,
                            1.0,
                            page.pageNumber(),
                            logicEvidence(text),
                            "正文对风险等级编号与风险名称的对应关系存在矛盾：" + String.join("；", mismatches),
                            "请人工核对风险等级定义、编号映射及后续风险说明是否应同步修正。",
                            "RULE",
                            true));
                }
            }
        }
    }

    private int firstSentenceEnd(String text, int from) {
        int period = text.indexOf('.', from);
        int semicolon = text.indexOf(';', from);
        if (period < 0) {
            return semicolon < 0 ? text.length() : semicolon;
        }
        if (semicolon < 0) {
            return period;
        }
        return Math.min(period, semicolon);
    }

    private String logicEvidence(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        int start = text.indexOf("风险程度");
        if (start < 0) {
            start = text.indexOf("风险等级");
        }
        if (start < 0) {
            start = 0;
        }
        int end = text.indexOf('。', start);
        if (end < 0) {
            end = text.indexOf('.', start);
        }
        if (end < 0) {
            end = Math.min(text.length(), start + 240);
        } else {
            end = Math.min(text.length(), end + 1);
        }
        return text.substring(start, end).replaceAll("\\s+", " ").strip();
    }

    /**
     * 从每一页提取候选产品代码：标签模式 + 产品库已知代码精确命中。
     */
    public List<EvidenceValue> extractProductCodeCandidates(List<DocumentPage> pages) {
        List<EvidenceValue> result = new ArrayList<>();
        Map<String, Boolean> seen = new LinkedHashMap<>(); // value|page -> dedup
        for (DocumentPage page : pages) {
            String text = page.normalizedText();
            if (!StringUtils.hasText(text)) {
                continue;
            }
            Matcher m = LABEL_PATTERN.matcher(text);
            while (m.find()) {
                String code = m.group(1);
                String key = code + "|" + page.pageNumber();
                if (seen.putIfAbsent(key, Boolean.TRUE) == null) {
                    result.add(new EvidenceValue(code, page.pageNumber(),
                            extractContext(text, m.start(), m.end()), "RULE_LABEL"));
                }
            }
            for (String known : productRepository.allProductCodes()) {
                int idx = text.indexOf(known);
                while (idx >= 0) {
                    String key = known + "|" + page.pageNumber();
                    if (seen.putIfAbsent(key, Boolean.TRUE) == null) {
                        result.add(new EvidenceValue(known, page.pageNumber(),
                                extractContext(text, idx, idx + known.length()), "RULE_MASTER_DATA"));
                    }
                    idx = text.indexOf(known, idx + known.length());
                }
            }
        }
        return result;
    }

    /** 从产品库标准名称、别名和系列名称中抽取正文候选产品名称。 */
    public List<EvidenceValue> extractProductNameCandidates(List<DocumentPage> pages) {
        List<EvidenceValue> result = new ArrayList<>();
        Map<String, Boolean> seen = new LinkedHashMap<>();
        List<String> names = productRepository.allProductNamesAndAliases();
        for (DocumentPage page : pages) {
            String text = page.normalizedText();
            if (!StringUtils.hasText(text)) {
                continue;
            }
            for (String name : names) {
                int idx = text.indexOf(name);
                while (idx >= 0) {
                    String key = name + "|" + page.pageNumber();
                    if (seen.putIfAbsent(key, Boolean.TRUE) == null) {
                        result.add(new EvidenceValue(name, page.pageNumber(),
                                extractContext(text, idx, idx + name.length()), "RULE_MASTER_NAME"));
                    }
                    idx = text.indexOf(name, idx + name.length());
                }
            }
        }
        return result;
    }

    private String extractContext(String text, int start, int end) {
        int from = Math.max(0, start - CONTEXT_RADIUS);
        int to = Math.min(text.length(), end + CONTEXT_RADIUS);
        String ctx = text.substring(from, to).replaceAll("\\s+", " ").strip();
        if (from > 0) {
            ctx = "…" + ctx;
        }
        if (to < text.length()) {
            ctx = ctx + "…";
        }
        return ctx;
    }

    private boolean shouldRaiseCodeConflict(DocumentType documentType,
                                            String declaredProductCode,
                                            Product targetProduct,
                                            Map<String, EvidenceValue> distinctCodes) {
        if (distinctCodes.size() < 2) {
            return false;
        }
        DocumentType type = documentType == null ? DocumentType.UNKNOWN : documentType;
        if (type.allowsMultipleProducts()) {
            return false;
        }
        if (!type.isSingleProductStrict()) {
            return false;
        }
        if (!StringUtils.hasText(declaredProductCode)) {
            return false;
        }
        String declared = declaredProductCode.strip();
        if (distinctCodes.containsKey(declared)) {
            return false;
        }
        if (distinctCodes.keySet().stream()
                .anyMatch(code -> familyResolver.isExactTargetCode(code, declared, targetProduct))) {
            return false;
        }
        return true;
    }

    private void detectCoreTemplateResidue(DocumentType documentType,
                                           String declaredProductCode,
                                           Product targetProduct,
                                           List<EvidenceValue> codeCandidates,
                                           List<ReviewIssue> issues) {
        DocumentType type = documentType == null ? DocumentType.UNKNOWN : documentType;
        if (!type.isSingleProductStrict() || !StringUtils.hasText(declaredProductCode)) {
            return;
        }
        String declared = declaredProductCode.strip();
        boolean targetSeen = codeCandidates.stream()
                .anyMatch(c -> familyResolver.isExactTargetCode(c.value(), declared, targetProduct));
        if (!targetSeen) {
            return;
        }
        for (EvidenceValue candidate : codeCandidates) {
            if (familyResolver.isExactTargetCode(candidate.value(), declared, targetProduct)) {
                continue;
            }
            String context = candidate.evidenceText() == null ? "" : candidate.evidenceText();
            if (isCoreProductContext(context) && !isNormalReferenceContext(context)) {
                issues.add(new ReviewIssue(
                        IssueType.POSSIBLE_TEMPLATE_RESIDUE,
                        Severity.HIGH,
                        0.86,
                        candidate.pageNumber(),
                        candidate.evidenceText(),
                        "单产品文件核心产品信息字段出现非目标产品代码，疑似模板残留。",
                        "建议人工核查该核心字段是否应替换为声明目标产品。",
                        "RULE",
                        true));
            }
        }
    }

    private boolean isCoreProductContext(String context) {
        return List.of("产品基本情况", "产品基本信息", "产品概况", "产品要素",
                        "理财产品基本情况", "产品名称", "产品代码", "登记编码")
                .stream()
                .anyMatch(context::contains);
    }

    private boolean isNormalReferenceContext(String context) {
        return List.of("案例", "示例", "举例", "例如", "风险说明", "参考", "假设")
                .stream()
                .anyMatch(context::contains);
    }

    /** 规则审核输出 */
    public record RuleReviewOutcome(
            List<EvidenceValue> productCodeCandidates,
            List<EvidenceValue> productNameCandidates,
            List<EvidenceValue> placeholders,
            List<ReviewIssue> issues
    ) {
        public RuleReviewOutcome(List<EvidenceValue> productCodeCandidates,
                                 List<EvidenceValue> placeholders,
                                 List<ReviewIssue> issues) {
            this(productCodeCandidates, List.of(), placeholders, issues);
        }

        public RuleReviewOutcome {
            productCodeCandidates = productCodeCandidates == null ? List.of() : List.copyOf(productCodeCandidates);
            productNameCandidates = productNameCandidates == null ? List.of() : List.copyOf(productNameCandidates);
            placeholders = placeholders == null ? List.of() : List.copyOf(placeholders);
            issues = issues == null ? List.of() : List.copyOf(issues);
        }
    }
}
