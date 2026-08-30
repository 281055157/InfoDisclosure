package com.example.disclosurereview.llm;

import com.example.disclosurereview.config.LlmProperties;
import com.example.disclosurereview.config.ReviewProperties;
import com.example.disclosurereview.exception.LlmException;
import com.example.disclosurereview.model.DocumentPage;
import com.example.disclosurereview.model.DocumentScope;
import com.example.disclosurereview.model.DocumentTypeAssessment;
import com.example.disclosurereview.model.Evidence;
import com.example.disclosurereview.model.FieldAssessment;
import com.example.disclosurereview.model.IssueType;
import com.example.disclosurereview.model.LlmReviewResult;
import com.example.disclosurereview.model.AgencyAssessment;
import com.example.disclosurereview.model.BusinessAcceptanceDecision;
import com.example.disclosurereview.model.MatchBasis;
import com.example.disclosurereview.model.ProductIdentityDecision;
import com.example.disclosurereview.model.ProductOccurrence;
import com.example.disclosurereview.model.ProductReferenceRole;
import com.example.disclosurereview.model.ProductTableRow;
import com.example.disclosurereview.model.ProductReference;
import com.example.disclosurereview.model.ReviewIssue;
import com.example.disclosurereview.model.Severity;
import com.example.disclosurereview.model.TargetMatchDecision;
import com.example.disclosurereview.model.TargetProductAssessment;
import com.example.disclosurereview.rule.domain.SemanticRuleCheck;
import com.example.disclosurereview.rule.domain.SemanticRuleResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 大模型审核：负责分块调用、JSON 解析、枚举校验、证据回查、失败降级。
 */
@Service
public class LlmReviewService {

    private static final Logger log = LoggerFactory.getLogger(LlmReviewService.class);

    private final LlmGateway llmGateway;
    private final DocumentChunker chunker;
    private final EvidenceVerifier evidenceVerifier;
    private final LlmProperties llmProperties;
    private final ReviewProperties reviewProperties;
    private final ObjectMapper objectMapper;

    private final String systemPrompt;
    private final String userPromptTemplate;

    @Autowired
    public LlmReviewService(LlmGateway llmGateway,
                            DocumentChunker chunker,
                            EvidenceVerifier evidenceVerifier,
                            LlmProperties llmProperties,
                            ReviewProperties reviewProperties,
                            ObjectMapper objectMapper) {
        this.llmGateway = llmGateway;
        this.chunker = chunker;
        this.evidenceVerifier = evidenceVerifier;
        this.llmProperties = llmProperties;
        this.reviewProperties = reviewProperties;
        this.objectMapper = objectMapper;
        this.systemPrompt = loadPrompt("prompts/review-system-prompt.txt");
        this.userPromptTemplate = loadPrompt("prompts/review-user-prompt.txt");
    }

    public LlmReviewService(LlmClient llmClient,
                            DocumentChunker chunker,
                            EvidenceVerifier evidenceVerifier,
                            LlmProperties llmProperties,
                            ReviewProperties reviewProperties,
                            ObjectMapper objectMapper) {
        this(llmClient == null ? null : new LlmGateway(llmClient, llmProperties),
                chunker, evidenceVerifier, llmProperties, reviewProperties, objectMapper);
    }

    private String loadPrompt(String path) {
        try {
            ClassPathResource resource = new ClassPathResource(path);
            return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("无法加载提示词文件: " + path, e);
        }
    }

    /**
     * 执行 LLM 审核。
     *
     * @param pages                   PDF 页面
     * @param fileName                原始文件名
     * @param documentCategory        文件类别
     * @param declaredProductCode     声明产品代码
     * @param declaredDocumentType    声明文件类型
     * @param b9Value                 参数表 B9 值
     * @param productMasterDataJson   产品库匹配结果 JSON
     * @param ruleExtractedCodesJson  规则提取的代码 JSON
     * @return LLM 审核结果（证据已回查）
     * @throws LlmException 当全部块都调用失败或解析失败时抛出
     */
    public LlmReviewResult review(List<DocumentPage> pages,
                                  String fileName,
                                  String documentCategory,
                                  String declaredProductCode,
                                  String declaredDocumentType,
                                  String b9Value,
                                  String productMasterDataJson,
                                  String productFamilyDataJson,
                                  String targetBankNamesJson,
                                  String candidateDocumentType,
                                  String documentTypePolicy,
                                  String ruleExtractedCodesJson,
                                  String ruleExtractedNamesJson,
                                  String possibleTableRowsJson) {
        return reviewCombined(pages, fileName, documentCategory, declaredProductCode, declaredDocumentType,
                b9Value, productMasterDataJson, productFamilyDataJson, targetBankNamesJson,
                candidateDocumentType, documentTypePolicy, ruleExtractedCodesJson, ruleExtractedNamesJson,
                possibleTableRowsJson, List.of(), LlmCallContext.none())
                .result()
                .reviewResult();
    }

    public LlmGatewayResponse<CombinedLlmReviewResult> reviewCombined(List<DocumentPage> pages,
                                                                      String fileName,
                                                                      String documentCategory,
                                                                      String declaredProductCode,
                                                                      String declaredDocumentType,
                                                                      String b9Value,
                                                                      String productMasterDataJson,
                                                                      String productFamilyDataJson,
                                                                      String targetBankNamesJson,
                                                                      String candidateDocumentType,
                                                                      String documentTypePolicy,
                                                                      String ruleExtractedCodesJson,
                                                                      String ruleExtractedNamesJson,
                                                                      String possibleTableRowsJson,
                                                                      List<SemanticRuleCheck> semanticChecks,
                                                                      LlmCallContext callContext) {

        List<DocumentChunker.TextChunk> chunks = chunker.chunk(
                pages, llmProperties.getMaxInputChars(), llmProperties.getChunkChars());

        List<CombinedLlmReviewResult> results = new ArrayList<>();
        LlmGatewayResponse<CombinedLlmReviewResult> firstResponse = null;
        int failed = 0;
        for (int i = 0; i < chunks.size(); i++) {
            DocumentChunker.TextChunk chunk = chunks.get(i);
            try {
                String userPrompt = buildUserPrompt(
                        fileName, documentCategory, declaredProductCode, declaredDocumentType,
                        b9Value, productMasterDataJson, productFamilyDataJson,
                        targetBankNamesJson, candidateDocumentType, documentTypePolicy,
                        ruleExtractedCodesJson, ruleExtractedNamesJson, possibleTableRowsJson,
                        chunk.text(), semanticChecks);
                LlmGatewayResponse<CombinedLlmReviewResult> response = llmGateway.chatCompletion(
                        (callContext == null ? LlmCallContext.none() : callContext)
                                .withChunk(i + 1, chunk.fromPage(), chunk.toPage()),
                        systemPrompt,
                        userPrompt,
                        raw -> parseCombined(raw, pages));
                if (firstResponse == null) {
                    firstResponse = response;
                }
                results.add(response.result());
            } catch (LlmException e) {
                log.info("第 {}/{} 块 LLM 调用失败: {}", i + 1, chunks.size(), e.getMessage());
                failed++;
            }
        }

        if (results.isEmpty()) {
            throw new LlmException("全部 " + chunks.size() + " 个文本块调用失败");
        }
        if (failed > 0) {
            log.info("部分文本块调用失败: {}/{}", failed, chunks.size());
        }
        CombinedLlmReviewResult merged = mergeCombinedResults(results);
        if (firstResponse == null) {
            return new LlmGatewayResponse<>(merged, null, null, null, LlmUsage.empty());
        }
        return new LlmGatewayResponse<>(merged, firstResponse.modelCallRecord(),
                firstResponse.providerCode(), firstResponse.modelName(), firstResponse.usage());
    }

    private String buildUserPrompt(String fileName, String documentCategory,
                                   String declaredProductCode, String declaredDocumentType,
                                   String b9Value, String productMasterDataJson,
                                   String productFamilyDataJson, String targetBankNamesJson,
                                   String candidateDocumentType, String documentTypePolicy,
                                   String ruleExtractedCodesJson, String ruleExtractedNamesJson,
                                   String possibleTableRowsJson,
                                   String documentText,
                                   List<SemanticRuleCheck> semanticChecks) {
        String allowedTypes = String.join("、", reviewProperties.getAllowedDocumentTypes());
        String prompt = userPromptTemplate
                .replace("{{fileName}}", nullToEmpty(fileName))
                .replace("{{documentCategory}}", nullToEmpty(documentCategory))
                .replace("{{declaredProductCode}}", nullToEmpty(declaredProductCode))
                .replace("{{declaredDocumentType}}", nullToEmpty(declaredDocumentType))
                .replace("{{b9Value}}", nullToEmpty(b9Value))
                .replace("{{productMasterData}}", nullToEmpty(productMasterDataJson))
                .replace("{{targetProductMasterData}}", nullToEmpty(productMasterDataJson))
                .replace("{{productFamilyData}}", nullToEmpty(productFamilyDataJson))
                .replace("{{targetBankNames}}", nullToEmpty(targetBankNamesJson))
                .replace("{{candidateDocumentType}}", nullToEmpty(candidateDocumentType))
                .replace("{{documentTypePolicy}}", nullToEmpty(documentTypePolicy))
                .replace("{{allowedDocumentTypes}}", allowedTypes)
                .replace("{{ruleExtractedProductCodes}}", nullToEmpty(ruleExtractedCodesJson))
                .replace("{{allExtractedProductCodes}}", nullToEmpty(ruleExtractedCodesJson))
                .replace("{{allExtractedProductNames}}", nullToEmpty(ruleExtractedNamesJson))
                .replace("{{possibleTableRows}}", nullToEmpty(possibleTableRowsJson))
                .replace("{{documentText}}", nullToEmpty(documentText));
        if (semanticChecks == null || semanticChecks.isEmpty()) {
            return prompt;
        }
        return prompt + """

                本次还需要在同一次模型请求中完成以下语义规则复核。不得另行生成未列出的规则结果。
                语义规则列表：
                %s

                最外层必须返回 JSON 对象：
                {
                  "reviewResult": 上方要求的原审核JSON对象,
                  "semanticRuleResults": [
                    {
                      "ruleCode": "",
                      "violated": true,
                      "confidence": 0.0,
                      "pageNumber": 1,
                      "evidenceText": "",
                      "explanation": "",
                      "suggestion": ""
                    }
                  ]
                }
                semanticRuleResults 中每条 ruleCode 必须来自语义规则列表。低置信度或证据无法直接来自正文时 violated 返回 false。
                """.formatted(toJson(semanticChecks));
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "[]";
        }
    }

    /**
     * 解析并校验模型返回的 JSON，完成后做证据回查。
     */
    public LlmReviewResult parseAndValidate(String raw, List<DocumentPage> pages) {
        return parseCombined(raw, pages).reviewResult();
    }

    public CombinedLlmReviewResult parseCombined(String raw, List<DocumentPage> pages) {
        if (!StringUtils.hasText(raw)) {
            throw new LlmException("LLM返回内容为空");
        }
        // 尝试剥离 markdown 代码块
        String json = raw.strip();
        if (json.startsWith("```json")) {
            json = json.substring(7);
        } else if (json.startsWith("```")) {
            json = json.substring(3);
        }
        if (json.endsWith("```")) {
            json = json.substring(0, json.length() - 3);
        }
        json = json.strip();

        JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (Exception e) {
            throw new LlmException("LLM返回非法JSON: " + e.getMessage(), e);
        }
        JsonNode reviewNode = root.has("reviewResult") ? root.path("reviewResult") : root;
        LlmReviewResult reviewResult = parseAndValidate(reviewNode, pages);
        List<SemanticRuleResponse> semanticResults = parseSemanticRuleResponses(root.path("semanticRuleResults"));
        return new CombinedLlmReviewResult(reviewResult, semanticResults);
    }

    private LlmReviewResult parseAndValidate(JsonNode root, List<DocumentPage> pages) {
        FieldAssessment mainCode = parseFieldAssessment(root.path("mainProductCode"), pages);
        FieldAssessment mainName = parseFieldAssessment(root.path("mainProductName"), pages);
        DocumentTypeAssessment docType = parseDocumentTypeAssessment(root.path("candidateDocumentType"), pages);
        List<ProductReference> references = parseProductReferences(root.path("otherProductReferences"), pages);
        DocumentScope documentScope = enumValue(DocumentScope.class, root.path("documentScope"), null);
        TargetProductAssessment targetAssessment = parseTargetProductAssessment(root.path("targetProductAssessment"), pages);
        List<ProductTableRow> targetRows = parseProductTableRows(root.path("targetProductRows"), pages);
        List<ProductOccurrence> occurrences = parseProductOccurrences(root.path("productOccurrences"));
        AgencyAssessment agencyAssessment = parseAgencyAssessment(root.path("agencyAssessment"), pages);
        List<ReviewIssue> issues = parseIssues(root.path("issues"), pages);
        String summary = textValue(root.path("summary"));
        String suggestion = textValue(root.path("manualReviewSuggestion"));

        return new LlmReviewResult(mainCode, mainName, docType, references,
                documentScope, targetAssessment, targetRows, occurrences, agencyAssessment,
                issues, summary, suggestion);
    }

    private List<SemanticRuleResponse> parseSemanticRuleResponses(JsonNode array) {
        List<SemanticRuleResponse> responses = new ArrayList<>();
        if (array != null && array.isArray()) {
            for (JsonNode node : array) {
                String ruleCode = textValue(node.path("ruleCode"));
                if (!StringUtils.hasText(ruleCode)) {
                    continue;
                }
                responses.add(new SemanticRuleResponse(
                        ruleCode,
                        booleanValue(node.path("violated"), false),
                        confidenceValue(node.path("confidence")),
                        intValue(node.path("pageNumber")),
                        textValue(node.path("evidenceText")),
                        textValue(node.path("explanation")),
                        textValue(node.path("suggestion"))));
            }
        }
        return responses;
    }

    private FieldAssessment parseFieldAssessment(JsonNode node, List<DocumentPage> pages) {
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = textValue(node.path("value"));
        Double confidence = confidenceValue(node.path("confidence"));
        List<Evidence> evidence = parseEvidence(node.path("evidence"), pages);
        return new FieldAssessment(value, confidence, evidence);
    }

    private DocumentTypeAssessment parseDocumentTypeAssessment(JsonNode node, List<DocumentPage> pages) {
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = textValue(node.path("value"));
        Double confidence = confidenceValue(node.path("confidence"));
        String reason = textValue(node.path("reason"));
        List<Evidence> evidence = parseEvidence(node.path("evidence"), pages);
        return new DocumentTypeAssessment(value, confidence, reason, evidence);
    }

    private List<Evidence> parseEvidence(JsonNode array, List<DocumentPage> pages) {
        List<Evidence> list = new ArrayList<>();
        if (array.isArray()) {
            for (JsonNode e : array) {
                Integer page = intValue(e.path("pageNumber"));
                String text = textValue(e.path("text"));
                boolean verified = evidenceVerifier.verifyText(page, text, pages);
                if (verified) {
                    list.add(new Evidence(page, text, true));
                }
            }
        }
        return list;
    }

    private List<ProductReference> parseProductReferences(JsonNode array, List<DocumentPage> pages) {
        List<ProductReference> list = new ArrayList<>();
        if (array.isArray()) {
            for (JsonNode e : array) {
                Integer page = intValue(e.path("pageNumber"));
                String text = textValue(e.path("text"));
                boolean verified = evidenceVerifier.verifyText(page, text, pages);
                if (verified) {
                    IssueType assessment = enumValue(IssueType.class, e.path("assessment"), IssueType.PRODUCT_REFERENCE);
                    list.add(new ProductReference(
                            textValue(e.path("productCode")),
                            textValue(e.path("productName")),
                            page, text, assessment,
                            confidenceValue(e.path("confidence")),
                            true));
                }
            }
        }
        return list;
    }

    private TargetProductAssessment parseTargetProductAssessment(JsonNode node, List<DocumentPage> pages) {
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        TargetMatchDecision decision = enumValue(TargetMatchDecision.class, node.path("decision"), TargetMatchDecision.UNKNOWN);
        ProductIdentityDecision identity = enumValue(ProductIdentityDecision.class,
                node.path("productIdentityDecision"), TargetProductAssessment.identityFrom(decision));
        BusinessAcceptanceDecision business = enumValue(BusinessAcceptanceDecision.class,
                node.path("businessAcceptanceDecision"), TargetProductAssessment.acceptanceFrom(decision));
        DocumentScope scope = enumValue(DocumentScope.class, node.path("documentScope"), DocumentScope.UNKNOWN);
        List<MatchBasis> bases = parseMatchBases(node.has("matchBases") ? node.path("matchBases") : node.path("matchBasis"));
        List<Evidence> evidence = parseEvidence(node.path("evidence"), pages);
        return new TargetProductAssessment(
                decision,
                identity,
                business,
                scope,
                bases,
                textValue(node.path("declaredProductCode")),
                textValue(node.path("matchedProductCode")),
                textValue(node.path("matchedProductName")),
                textValue(node.path("matchedProductSeries")),
                textValue(node.path("matchedInstitution")),
                evidence,
                confidenceValue(node.path("confidence")),
                textValue(node.path("explanation")),
                textValue(node.path("manualReviewSuggestion")));
    }

    private List<MatchBasis> parseMatchBases(JsonNode array) {
        List<MatchBasis> list = new ArrayList<>();
        if (array.isArray()) {
            for (JsonNode node : array) {
                list.add(enumValue(MatchBasis.class, node, null));
            }
        }
        return list.stream().filter(b -> b != null).distinct().toList();
    }

    private List<ProductTableRow> parseProductTableRows(JsonNode array, List<DocumentPage> pages) {
        List<ProductTableRow> list = new ArrayList<>();
        if (array.isArray()) {
            for (JsonNode row : array) {
                Integer page = intValue(row.path("pageNumber"));
                String evidenceText = textValue(row.path("evidenceText"));
                boolean verified = !StringUtils.hasText(evidenceText) || evidenceVerifier.verifyText(page, evidenceText, pages);
                if (verified) {
                    list.add(new ProductTableRow(
                            textValue(row.path("productName")),
                            textValue(row.path("registrationCode")),
                            textValue(row.path("productCode")),
                            textValue(row.path("salesCode")),
                            textValue(row.path("valuationDate")),
                            textValue(row.path("unitNav")),
                            textValue(row.path("accumulatedNav")),
                            page,
                            evidenceText,
                            confidenceValue(row.path("confidence"))));
                }
            }
        }
        return list;
    }

    private List<ProductOccurrence> parseProductOccurrences(JsonNode array) {
        List<ProductOccurrence> list = new ArrayList<>();
        if (array.isArray()) {
            for (JsonNode row : array) {
                list.add(new ProductOccurrence(
                        textValue(row.path("productCode")),
                        textValue(row.path("productName")),
                        enumValue(ProductReferenceRole.class, row.path("role"), ProductReferenceRole.UNKNOWN),
                        intValue(row.path("pageNumber")),
                        textValue(row.path("evidenceText")),
                        confidenceValue(row.path("confidence"))));
            }
        }
        return list;
    }

    private AgencyAssessment parseAgencyAssessment(JsonNode node, List<DocumentPage> pages) {
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        List<Evidence> evidence = parseEvidence(node.path("evidence"), pages);
        boolean isDistributionAgreement = booleanValue(node.path("isDistributionAgreement"), booleanValue(node.path("distributionAgreement"), false));
        return new AgencyAssessment(
                isDistributionAgreement,
                booleanValue(node.path("targetBankIsDistributor"), false),
                textValue(node.path("institutionName")),
                textValue(node.path("role")),
                confidenceValue(node.path("confidence")),
                evidence);
    }

    private List<ReviewIssue> parseIssues(JsonNode array, List<DocumentPage> pages) {
        List<ReviewIssue> list = new ArrayList<>();
        if (array.isArray()) {
            for (JsonNode e : array) {
                // 兼容旧模型输出，但不再把占位符作为审查问题处理。
                if ("PLACEHOLDER_NOT_REPLACED".equalsIgnoreCase(textValue(e.path("issueType")))) {
                    continue;
                }
                IssueType type = enumValue(IssueType.class, e.path("issueType"), IssueType.UNKNOWN_ISSUE);
                Severity severity = enumValue(Severity.class, e.path("severity"), Severity.UNKNOWN);
                Double confidence = confidenceValue(e.path("confidence"));
                Integer page = intValue(e.path("pageNumber"));
                String evidenceText = textValue(e.path("evidenceText"));
                boolean verified = evidenceVerifier.verifyText(page, evidenceText, pages);
                if (verified) {
                    list.add(new ReviewIssue(
                            type, severity, confidence, page, evidenceText,
                            textValue(e.path("explanation")),
                            textValue(e.path("suggestion")),
                            "LLM",
                            true));
                }
            }
        }
        return list;
    }

    private String textValue(JsonNode node) {
        if (node.isMissingNode() || node.isNull() || node.asText().isBlank()) {
            return null;
        }
        String v = node.asText().strip();
        return "UNKNOWN".equalsIgnoreCase(v) ? null : v;
    }

    private Double confidenceValue(JsonNode node) {
        if (node.isMissingNode() || node.isNull() || !node.isNumber()) {
            return null;
        }
        double v = node.asDouble();
        if (v < 0.0 || v > 1.0) {
            return null;
        }
        return v;
    }

    private Integer intValue(JsonNode node) {
        if (node.isMissingNode() || node.isNull() || !node.isInt()) {
            return null;
        }
        return node.asInt();
    }

    private boolean booleanValue(JsonNode node, boolean fallback) {
        if (node.isMissingNode() || node.isNull() || !node.isBoolean()) {
            return fallback;
        }
        return node.asBoolean();
    }

    private <T extends Enum<T>> T enumValue(Class<T> enumClass, JsonNode node, T fallback) {
        if (node.isMissingNode() || node.isNull() || !node.isTextual()) {
            return fallback;
        }
        try {
            return Enum.valueOf(enumClass, node.asText().strip().toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    /**
     * 多块结果合并：相同字段取置信度最高，问题按 (type, evidenceText) 去重。
     */
    private LlmReviewResult mergeResults(List<LlmReviewResult> results) {
        if (results.size() == 1) {
            return results.get(0);
        }
        FieldAssessment bestCode = null;
        FieldAssessment bestName = null;
        DocumentTypeAssessment bestType = null;
        DocumentScope documentScope = null;
        TargetProductAssessment bestTargetAssessment = null;
        AgencyAssessment bestAgencyAssessment = null;
        List<ProductReference> refs = new ArrayList<>();
        List<ProductTableRow> targetRows = new ArrayList<>();
        List<ProductOccurrence> occurrences = new ArrayList<>();
        List<ReviewIssue> issues = new ArrayList<>();
        String summary = null;
        String suggestion = null;

        for (LlmReviewResult r : results) {
            bestCode = pickHigher(bestCode, r.mainProductCode());
            bestName = pickHigher(bestName, r.mainProductName());
            bestType = pickHigher(bestType, r.candidateDocumentType());
            if (documentScope == null && r.documentScope() != null) {
                documentScope = r.documentScope();
            }
            bestTargetAssessment = pickHigher(bestTargetAssessment, r.targetProductAssessment());
            bestAgencyAssessment = pickHigher(bestAgencyAssessment, r.agencyAssessment());
            refs.addAll(r.otherProductReferences());
            targetRows.addAll(r.targetProductRows());
            occurrences.addAll(r.productOccurrences());
            issues.addAll(r.issues());
            if (StringUtils.hasText(r.summary()) && !StringUtils.hasText(summary)) {
                summary = r.summary();
            }
            if (StringUtils.hasText(r.manualReviewSuggestion()) && !StringUtils.hasText(suggestion)) {
                suggestion = r.manualReviewSuggestion();
            }
        }

        // 去重 refs 按 (code,name,page,text)
        List<ProductReference> distinctRefs = refs.stream()
                .distinct()
                .toList();
        List<ReviewIssue> distinctIssues = issues.stream()
                .distinct()
                .toList();

        return new LlmReviewResult(bestCode, bestName, bestType, distinctRefs,
                documentScope, bestTargetAssessment,
                targetRows.stream().distinct().toList(),
                occurrences.stream().distinct().toList(),
                bestAgencyAssessment, distinctIssues, summary, suggestion);
    }

    private CombinedLlmReviewResult mergeCombinedResults(List<CombinedLlmReviewResult> results) {
        List<LlmReviewResult> reviewResults = results.stream()
                .map(CombinedLlmReviewResult::reviewResult)
                .toList();
        List<SemanticRuleResponse> semanticResults = results.stream()
                .flatMap(result -> result.semanticRuleResults().stream())
                .toList();
        return new CombinedLlmReviewResult(mergeResults(reviewResults), semanticResults);
    }

    private <T> T pickHigher(T a, T b) {
        if (a == null) return b;
        if (b == null) return a;
        Double ca = confidenceOf(a);
        Double cb = confidenceOf(b);
        return (cb != null && (ca == null || cb > ca)) ? b : a;
    }

    private Double confidenceOf(Object o) {
        if (o instanceof FieldAssessment f) return f.confidence();
        if (o instanceof DocumentTypeAssessment d) return d.confidence();
        if (o instanceof TargetProductAssessment t) return t.confidence();
        if (o instanceof AgencyAssessment a) return a.confidence();
        return null;
    }
}
