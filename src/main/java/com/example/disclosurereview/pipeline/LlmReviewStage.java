package com.example.disclosurereview.pipeline;

import com.example.disclosurereview.config.LlmProperties;
import com.example.disclosurereview.config.ReviewProperties;
import com.example.disclosurereview.exception.LlmException;
import com.example.disclosurereview.llm.CombinedLlmReviewResult;
import com.example.disclosurereview.llm.LlmCallContext;
import com.example.disclosurereview.llm.LlmGatewayResponse;
import com.example.disclosurereview.llm.LlmReviewService;
import com.example.disclosurereview.model.AgencyAssessment;
import com.example.disclosurereview.model.BusinessAcceptanceDecision;
import com.example.disclosurereview.model.DocumentCategory;
import com.example.disclosurereview.model.DocumentPage;
import com.example.disclosurereview.model.DocumentScope;
import com.example.disclosurereview.model.DocumentType;
import com.example.disclosurereview.model.DocumentTypeAssessment;
import com.example.disclosurereview.model.LlmReviewResult;
import com.example.disclosurereview.model.Product;
import com.example.disclosurereview.model.ProductOccurrence;
import com.example.disclosurereview.model.ProductReferenceRole;
import com.example.disclosurereview.model.ProductTableRow;
import com.example.disclosurereview.model.ReviewIssue;
import com.example.disclosurereview.model.ReviewResult;
import com.example.disclosurereview.model.ReviewStage;
import com.example.disclosurereview.model.ReviewTaskStatus;
import com.example.disclosurereview.model.TechnicalStatus;
import com.example.disclosurereview.repository.ProductRepository;
import com.example.disclosurereview.rule.PromptPolicyProvider;
import com.example.disclosurereview.rule.RuleEngine;
import com.example.disclosurereview.rule.RuleReviewService;
import com.example.disclosurereview.rule.domain.RuleEvidence;
import com.example.disclosurereview.rule.domain.RuleExecutionResult;
import com.example.disclosurereview.rule.domain.SemanticRuleCheck;
import com.example.disclosurereview.rule.domain.SemanticRuleResponse;
import com.example.disclosurereview.strategy.DocumentReviewStrategy;
import com.example.disclosurereview.strategy.DocumentReviewStrategyRegistry;
import com.example.disclosurereview.strategy.DocumentTypeAliasResolver;
import com.example.disclosurereview.strategy.InstitutionRoleExtractor;
import com.example.disclosurereview.strategy.ProductCodeFamilyResolver;
import com.example.disclosurereview.strategy.ProductTableRowExtractor;
import com.example.disclosurereview.strategy.ReviewContext;
import com.example.disclosurereview.strategy.StrategyReviewPolicy;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class LlmReviewStage implements ReviewStageHandler {

    private static final TypeReference<RuleReviewService.RuleReviewOutcome> RULE_OUTCOME =
            new TypeReference<>() {
            };
    private static final TypeReference<List<SemanticRuleCheck>> SEMANTIC_CHECKS =
            new TypeReference<>() {
            };

    private final ReviewStageSupport support;
    private final ReviewTaskContextStore contextStore;
    private final ProductRepository productRepository;
    private final RuleEngine ruleEngine;
    private final LlmReviewService llmReviewService;
    private final LlmProperties llmProperties;
    private final ReviewProperties reviewProperties;
    private final ObjectMapper objectMapper;
    private final DocumentTypeAliasResolver documentTypeResolver;
    private final DocumentReviewStrategyRegistry strategyRegistry;
    private final ProductTableRowExtractor productTableRowExtractor;
    private final InstitutionRoleExtractor institutionRoleExtractor;
    private final PromptPolicyProvider promptPolicyProvider;
    private final com.example.disclosurereview.llm.EvidenceVerifier evidenceVerifier;

    public LlmReviewStage(ReviewStageSupport support,
                          ReviewTaskContextStore contextStore,
                          ProductRepository productRepository,
                          RuleEngine ruleEngine,
                          LlmReviewService llmReviewService,
                          LlmProperties llmProperties,
                          ReviewProperties reviewProperties,
                          ObjectMapper objectMapper,
                          DocumentTypeAliasResolver documentTypeResolver,
                          DocumentReviewStrategyRegistry strategyRegistry,
                          ProductTableRowExtractor productTableRowExtractor,
                          InstitutionRoleExtractor institutionRoleExtractor,
                          PromptPolicyProvider promptPolicyProvider,
                          com.example.disclosurereview.llm.EvidenceVerifier evidenceVerifier) {
        this.support = support;
        this.contextStore = contextStore;
        this.productRepository = productRepository;
        this.ruleEngine = ruleEngine;
        this.llmReviewService = llmReviewService;
        this.llmProperties = llmProperties;
        this.reviewProperties = reviewProperties;
        this.objectMapper = objectMapper;
        this.documentTypeResolver = documentTypeResolver;
        this.strategyRegistry = strategyRegistry;
        this.productTableRowExtractor = productTableRowExtractor;
        this.institutionRoleExtractor = institutionRoleExtractor;
        this.promptPolicyProvider = promptPolicyProvider == null ? PromptPolicyProvider.disabled() : promptPolicyProvider;
        this.evidenceVerifier = evidenceVerifier;
    }

    @Override
    public ReviewStage stage() {
        return ReviewStage.LLM_REVIEWING;
    }

    @Override
    public int order() {
        return 50;
    }

    @Override
    public StageResult handle(ReviewPipelineContext context) {
        Long taskId = context.getTaskId();
        if (support.getTask(taskId).getStatus() != ReviewTaskStatus.LLM_REVIEWING) {
            support.transition(taskId, ReviewTaskStatus.LLM_REVIEWING,
                    context.isRetry() ? "Retry LLM review stage" : "Start LLM review");
        }
        support.updateStage(taskId, ReviewStage.LLM_REVIEWING);
        var task = support.getTask(taskId);
        List<DocumentPage> pages = support.persistedPages(taskId);
        if (pages.isEmpty()) {
            throw new IllegalStateException("PDF页面尚未持久化，无法执行LLM审核");
        }

        RuleReviewService.RuleReviewOutcome ruleOutcome = readRuleOutcome(taskId);
        List<SemanticRuleCheck> semanticChecks = readSemanticChecks(taskId);
        Product matchedProduct = StringUtils.hasText(task.getDeclaredProductCode())
                ? productRepository.findAny(task.getDeclaredProductCode()).orElse(null)
                : null;

        DocumentType declaredType = documentTypeResolver.resolve(task.getDeclaredDocumentType());
        DocumentType preLlmCandidateType = documentTypeResolver.detectFromPages(pages);
        DocumentType strategyCandidate = declaredType != DocumentType.UNKNOWN ? declaredType : preLlmCandidateType;
        DocumentReviewStrategy strategy = strategyRegistry.select(declaredType, preLlmCandidateType);
        ReviewContext preLlmContext = new ReviewContext(
                pages,
                task.getOriginalFileName(),
                task.getDocumentCategory(),
                task.getDeclaredProductCode(),
                task.getDeclaredDocumentType(),
                declaredType,
                preLlmCandidateType,
                task.getB9Value(),
                matchedProduct,
                reviewProperties.getInstitution().getTargetBankNames());
        StrategyReviewPolicy policy = strategy.buildPolicy(preLlmContext);
        List<ProductTableRow> ruleTargetRows = productTableRowExtractor.extractTargetRows(
                pages, task.getDeclaredProductCode(), matchedProduct);
        List<ProductOccurrence> ruleOccurrences = buildProductOccurrences(
                ruleOutcome, task.getDeclaredProductCode(), matchedProduct);

        LlmReviewResult llmResult = null;
        String llmFailureDetail = null;
        Long modelCallId = null;
        if (!llmProperties.isEnabled()) {
            llmFailureDetail = "LLM审核已关闭，仅使用规则结果";
        } else {
            try {
                LlmGatewayResponse<CombinedLlmReviewResult> response = llmReviewService.reviewCombined(
                        pages,
                        task.getOriginalFileName(),
                        task.getDocumentCategory().name(),
                        task.getDeclaredProductCode(),
                        task.getDeclaredDocumentType(),
                        task.getB9Value(),
                        toJson(productMasterJson(matchedProduct, task.getDeclaredProductCode())),
                        toJson(productFamilyJson(matchedProduct, task.getDeclaredProductCode())),
                        toJson(reviewProperties.getInstitution().getTargetBankNames()),
                        preLlmCandidateType == DocumentType.UNKNOWN ? "" : preLlmCandidateType.displayName(),
                        mergePromptPolicy(policy.promptPolicy(),
                                promptPolicyProvider.additionalPolicy(task.getDocumentCategory(), strategyCandidate,
                                        task.getDeclaredProductCode(), task.getDeclaredDocumentType(), matchedProduct)),
                        toJson(ruleOutcome.productCodeCandidates()),
                        toJson(ruleOutcome.productNameCandidates()),
                        toJson(ruleTargetRows),
                        semanticChecks,
                        new LlmCallContext(taskId, ReviewStage.LLM_REVIEWING, "COMBINED_REVIEW",
                                null, null, null, null, null));
                llmResult = response.result().reviewResult();
                modelCallId = response.modelCallRecord() == null ? null : response.modelCallRecord().getId();
                ruleOutcome = mergeSemanticRuleResults(ruleOutcome, semanticChecks,
                        response.result().semanticRuleResults(),
                        pages,
                        response.modelCallRecord() == null || response.modelCallRecord().getDurationMs() == null
                                ? 0L
                                : response.modelCallRecord().getDurationMs());
            } catch (LlmException e) {
                llmFailureDetail = e.getMessage();
            } catch (Exception e) {
                llmFailureDetail = e.getMessage();
            }
        }
        if (llmFailureDetail != null) {
            for (SemanticRuleCheck check : semanticChecks) {
                ruleEngine.updateDeferredSemanticExecution(check,
                        RuleExecutionResult.indeterminate("LLM_FAILED: " + llmFailureDetail), 0);
            }
        }

        DocumentType llmCandidateType = llmResult != null && llmResult.candidateDocumentType() != null
                ? documentTypeResolver.resolve(llmResult.candidateDocumentType().value())
                : DocumentType.UNKNOWN;
        DocumentType finalCandidateType = llmCandidateType != DocumentType.UNKNOWN ? llmCandidateType : preLlmCandidateType;
        DocumentReviewStrategy finalStrategy = strategyRegistry.select(declaredType, finalCandidateType);
        ReviewContext finalContext = new ReviewContext(
                pages,
                task.getOriginalFileName(),
                task.getDocumentCategory(),
                task.getDeclaredProductCode(),
                task.getDeclaredDocumentType(),
                declaredType,
                finalCandidateType,
                task.getB9Value(),
                matchedProduct,
                reviewProperties.getInstitution().getTargetBankNames());
        var targetAssessment = finalStrategy.evaluate(finalContext, ruleOutcome, llmResult);
        DocumentScope documentScope = targetAssessment == null ? null : targetAssessment.documentScope();
        if (documentScope == null && llmResult != null) {
            documentScope = llmResult.documentScope();
        }
        DocumentTypeAssessment candidateAssessment = candidateTypeAssessment(finalCandidateType, llmResult, preLlmCandidateType);
        List<ProductTableRow> targetRows = mergeLists(ruleTargetRows,
                llmResult == null ? List.of() : llmResult.targetProductRows());
        List<ProductOccurrence> productOccurrences = mergeLists(ruleOccurrences,
                llmResult == null ? List.of() : llmResult.productOccurrences());
        AgencyAssessment agencyAssessment = bestAgencyAssessment(
                institutionRoleExtractor.assess(pages, reviewProperties.getInstitution().getTargetBankNames(),
                        finalCandidateType == DocumentType.DISTRIBUTION_AGREEMENT
                                || declaredType == DocumentType.DISTRIBUTION_AGREEMENT),
                llmResult == null ? null : llmResult.agencyAssessment());

        TechnicalStatus status = llmFailureDetail != null
                ? TechnicalStatus.LLM_FAILED
                : (task.getTechnicalStatus() == TechnicalStatus.EXCEL_PARSE_FAILED
                ? TechnicalStatus.PARTIAL_SUCCESS
                : TechnicalStatus.SUCCESS);
        String statusDetail = llmFailureDetail == null
                ? (status == TechnicalStatus.PARTIAL_SUCCESS ? task.getStatusDetail() : null)
                : "LLM调用失败: " + llmFailureDetail;
        ReviewResult draft = new ReviewResult(
                task.getTaskNo(),
                status,
                null,
                new ReviewResult.FileInfo(task.getOriginalFileName(), task.getDocumentCategory(), pages.size()),
                new ReviewResult.DeclaredInfo(task.getDeclaredProductCode(), task.getDeclaredDocumentType(), task.getB9Value()),
                toProductMasterInfo(matchedProduct),
                new ReviewResult.RuleResultInfo(
                        ruleOutcome.productCodeCandidates(),
                        ruleOutcome.productNameCandidates(),
                        ruleOutcome.placeholders(),
                        ruleOutcome.issues()),
                llmResult == null ? ReviewResult.LlmResultInfo.empty() : ReviewResult.LlmResultInfo.from(llmResult),
                documentScope,
                candidateAssessment,
                targetAssessment,
                targetRows,
                productOccurrences,
                agencyAssessment,
                List.of(),
                statusDetail,
                task.getCreatedAt(),
                Instant.now());

        ObjectNode root = contextStore.load(taskId);
        Map<String, Object> llmReview = new LinkedHashMap<>();
        llmReview.put("modelCallId", modelCallId);
        llmReview.put("llmFailed", llmFailureDetail != null);
        llmReview.put("statusDetail", statusDetail);
        root.set("llmReview", objectMapper.valueToTree(llmReview));
        root.set("reviewDraft", objectMapper.valueToTree(draft));
        root.set("ruleReview", objectMapper.valueToTree(Map.of(
                "ruleOutcome", ruleOutcome,
                "semanticChecks", semanticChecks,
                "declaredDocumentType", declaredType.name(),
                "preLlmCandidateType", preLlmCandidateType.name(),
                "strategyCandidateType", strategyCandidate.name())));
        contextStore.save(taskId, root);
        return StageResult.completed(stage(), llmFailureDetail == null
                ? "LLM review completed"
                : "LLM review failed; rule result retained");
    }

    private RuleReviewService.RuleReviewOutcome readRuleOutcome(Long taskId) {
        return contextStore.read(taskId, "ruleReview/ruleOutcome", RULE_OUTCOME)
                .orElseGet(() -> {
                    ObjectNode root = contextStore.load(taskId);
                    if (root.path("ruleReview").path("ruleOutcome").isMissingNode()) {
                        return new RuleReviewService.RuleReviewOutcome(List.of(), List.of(), List.of(), List.of());
                    }
                    return objectMapper.convertValue(root.path("ruleReview").path("ruleOutcome"), RULE_OUTCOME);
                });
    }

    private List<SemanticRuleCheck> readSemanticChecks(Long taskId) {
        ObjectNode root = contextStore.load(taskId);
        if (root.path("ruleReview").path("semanticChecks").isMissingNode()) {
            return List.of();
        }
        return objectMapper.convertValue(root.path("ruleReview").path("semanticChecks"), SEMANTIC_CHECKS);
    }

    private RuleReviewService.RuleReviewOutcome mergeSemanticRuleResults(RuleReviewService.RuleReviewOutcome base,
                                                                        List<SemanticRuleCheck> checks,
                                                                        List<SemanticRuleResponse> responses,
                                                                        List<DocumentPage> pages,
                                                                        long durationMs) {
        List<ReviewIssue> issues = new ArrayList<>(base.issues());
        for (SemanticRuleCheck check : checks) {
            List<SemanticRuleResponse> matchedResponses = responses.stream()
                    .filter(response -> check.ruleCode().equals(response.ruleCode()))
                    .toList();
            RuleExecutionResult executionResult = semanticExecutionResult(check, matchedResponses, pages);
            ruleEngine.updateDeferredSemanticExecution(check, executionResult, durationMs);
            issues.addAll(executionResult.issues());
        }
        return new RuleReviewService.RuleReviewOutcome(base.productCodeCandidates(),
                base.productNameCandidates(), base.placeholders(), issues);
    }

    RuleExecutionResult semanticExecutionResult(SemanticRuleCheck check,
                                                List<SemanticRuleResponse> responses,
                                                List<DocumentPage> pages) {
        if (responses == null || responses.isEmpty()) {
            return RuleExecutionResult.indeterminate("LLM_SEMANTIC_RESPONSE_MISSING");
        }
        List<ReviewIssue> issues = new ArrayList<>();
        List<RuleEvidence> evidence = new ArrayList<>();
        List<String> indeterminate = new ArrayList<>();
        List<String> notHitReasons = new ArrayList<>();
        for (SemanticRuleResponse response : responses) {
            double confidence = response.confidence() == null ? 0.0 : response.confidence();
            if (!response.violated()) {
                if (confidence < check.minConfidence()) {
                    indeterminate.add("LOW_CONFIDENCE: " + confidence + ", minConfidence=" + check.minConfidence());
                } else {
                    String explanation = StringUtils.hasText(response.explanation())
                            ? response.explanation() : "模型未发现满足规则条件的违规内容";
                    notHitReasons.add("confidence=" + confidence + "；" + explanation);
                }
                continue;
            }
            if (confidence < check.minConfidence()) {
                indeterminate.add("LOW_CONFIDENCE: " + confidence);
                continue;
            }
            if (!evidenceVerifier.verifyText(response.pageNumber(), response.evidenceText(), pages)) {
                indeterminate.add("EVIDENCE_NOT_VERIFIED");
                continue;
            }
            ReviewIssue issue = new ReviewIssue(
                    check.action().issueType(),
                    check.action().severity(),
                    Math.max(check.action().confidence(), confidence),
                    response.pageNumber(),
                    response.evidenceText(),
                    StringUtils.hasText(response.explanation())
                            ? response.explanation()
                            : render(check.action().explanationTemplate(), response.evidenceText()),
                    StringUtils.hasText(response.suggestion())
                            ? response.suggestion()
                            : render(check.action().suggestionTemplate(), response.evidenceText()),
                    check.action().source(),
                    true)
                    .withRuleTrace(check.ruleCode(), check.ruleVersionId(), check.executionId());
            issues.add(issue);
            evidence.add(new RuleEvidence(response.pageNumber(), response.evidenceText(), check.executorType(), true));
        }
        if (!issues.isEmpty()) {
            return RuleExecutionResult.hit(issues, evidence, "confirmed=" + issues.size());
        }
        return indeterminate.isEmpty() ? RuleExecutionResult.notHit(String.join("；", notHitReasons))
                : RuleExecutionResult.indeterminate(String.join("; ", indeterminate));
    }

    private String render(String template, String detail) {
        return (template == null ? "" : template).replace("${detail}", detail == null ? "" : detail)
                .replace("${llmExplanation}", detail == null ? "" : detail);
    }

    private ReviewResult.ProductMasterInfo toProductMasterInfo(Product product) {
        if (product == null) {
            return ReviewResult.ProductMasterInfo.notMatched();
        }
        return new ReviewResult.ProductMasterInfo(true, product.productCode(),
                product.productName(), product.safeAliases(), product.managerName(), product.issuerName(),
                product.parentProductCode(), product.safeShareCodes(), product.safeCodeAliases(),
                product.safeSeriesNames(), product.safeDistributorNames(), product.productType());
    }

    private Object productMasterJson(Product product, String declaredProductCode) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("matched", product != null);
        data.put("lookupStatus", product == null ? "NOT_FOUND" : "MATCHED");
        data.put("declaredProductCode", declaredProductCode == null ? "" : declaredProductCode);
        if (product == null) {
            data.put("knownProductCodes", productRepository.allProductCodes());
            data.put("lookupMessage", "声明产品代码未在当前产品库中找到，不得自动匹配到其他已知产品");
            return data;
        }
        data.put("productCode", product.productCode() == null ? "" : product.productCode());
        data.put("productName", product.productName() == null ? "" : product.productName());
        data.put("aliases", product.safeAliases());
        data.put("managerName", product.managerName() == null ? "" : product.managerName());
        data.put("issuerName", product.issuerName() == null ? "" : product.issuerName());
        data.put("parentProductCode", product.parentProductCode() == null ? "" : product.parentProductCode());
        data.put("shareCodes", product.safeShareCodes());
        data.put("codeAliases", product.safeCodeAliases());
        data.put("seriesNames", product.safeSeriesNames());
        data.put("distributorNames", product.safeDistributorNames());
        data.put("productType", product.productType() == null ? "" : product.productType());
        return data;
    }

    private Object productFamilyJson(Product product, String declaredProductCode) {
        if (product == null) {
            return Map.of(
                    "matched", false,
                    "lookupStatus", "NOT_FOUND",
                    "declaredProductCode", declaredProductCode == null ? "" : declaredProductCode,
                    "knownProductCodes", productRepository.allProductCodes());
        }
        return Map.of(
                "matched", true,
                "lookupStatus", "MATCHED",
                "declaredProductCode", declaredProductCode == null ? "" : declaredProductCode,
                "parentProductCode", product.parentProductCode() == null ? "" : product.parentProductCode(),
                "shareCodes", product.safeShareCodes(),
                "codeAliases", product.safeCodeAliases(),
                "seriesNames", product.safeSeriesNames());
    }

    private List<ProductOccurrence> buildProductOccurrences(RuleReviewService.RuleReviewOutcome ruleOutcome,
                                                            String declaredProductCode,
                                                            Product targetProduct) {
        if (ruleOutcome == null || ruleOutcome.productCodeCandidates() == null) {
            return List.of();
        }
        ProductCodeFamilyResolver familyResolver = new ProductCodeFamilyResolver();
        return ruleOutcome.productCodeCandidates().stream()
                .map(c -> new ProductOccurrence(
                        c.value(),
                        targetProduct != null && familyResolver.isExactTargetCode(c.value(), declaredProductCode, targetProduct)
                                ? targetProduct.productName()
                                : null,
                        familyResolver.isExactTargetCode(c.value(), declaredProductCode, targetProduct)
                                ? ProductReferenceRole.TARGET_PRODUCT
                                : ProductReferenceRole.CO_DISCLOSED_PRODUCT,
                        c.pageNumber(),
                        c.evidenceText(),
                        familyResolver.isExactTargetCode(c.value(), declaredProductCode, targetProduct) ? 0.9 : 0.55))
                .distinct()
                .toList();
    }

    private DocumentTypeAssessment candidateTypeAssessment(DocumentType finalCandidateType,
                                                           LlmReviewResult llmResult,
                                                           DocumentType preLlmCandidateType) {
        if (llmResult != null && llmResult.candidateDocumentType() != null) {
            return llmResult.candidateDocumentType();
        }
        DocumentType type = finalCandidateType != DocumentType.UNKNOWN ? finalCandidateType : preLlmCandidateType;
        if (type == null || type == DocumentType.UNKNOWN) {
            return null;
        }
        return new DocumentTypeAssessment(type.displayName(), 0.55,
                "规则根据文件名、B9或正文关键词得到的候选文件类型。", List.of());
    }

    private <T> List<T> mergeLists(List<T> first, List<T> second) {
        List<T> merged = new ArrayList<>();
        if (first != null) {
            merged.addAll(first);
        }
        if (second != null) {
            merged.addAll(second);
        }
        return merged.stream().distinct().toList();
    }

    private AgencyAssessment bestAgencyAssessment(AgencyAssessment ruleAssessment,
                                                  AgencyAssessment llmAssessment) {
        if (llmAssessment == null) {
            return ruleAssessment;
        }
        if (ruleAssessment == null) {
            return llmAssessment;
        }
        double ruleConfidence = ruleAssessment.confidence() == null ? 0.0 : ruleAssessment.confidence();
        double llmConfidence = llmAssessment.confidence() == null ? 0.0 : llmAssessment.confidence();
        return llmConfidence > ruleConfidence ? llmAssessment : ruleAssessment;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String mergePromptPolicy(String base, String dynamicPolicy) {
        if (!StringUtils.hasText(dynamicPolicy)) {
            return base;
        }
        if (!StringUtils.hasText(base)) {
            return dynamicPolicy;
        }
        return base + "\n" + dynamicPolicy;
    }
}
