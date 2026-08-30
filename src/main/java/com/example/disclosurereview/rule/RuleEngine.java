package com.example.disclosurereview.rule;

import com.example.disclosurereview.model.DocumentCategory;
import com.example.disclosurereview.model.DocumentPage;
import com.example.disclosurereview.model.DocumentType;
import com.example.disclosurereview.model.EvidenceValue;
import com.example.disclosurereview.model.Product;
import com.example.disclosurereview.model.ReviewIssue;
import com.example.disclosurereview.persistence.entity.ReviewRuleDefinitionEntity;
import com.example.disclosurereview.persistence.entity.ReviewRuleExecutionEntity;
import com.example.disclosurereview.persistence.entity.ReviewRuleVersionEntity;
import com.example.disclosurereview.persistence.entity.ReviewTaskEntity;
import com.example.disclosurereview.persistence.repository.ReviewRuleDefinitionJpaRepository;
import com.example.disclosurereview.persistence.repository.ReviewRuleExecutionJpaRepository;
import com.example.disclosurereview.persistence.repository.ReviewRuleVersionJpaRepository;
import com.example.disclosurereview.persistence.repository.ReviewTaskJpaRepository;
import com.example.disclosurereview.rule.domain.RuleExecutionContext;
import com.example.disclosurereview.rule.domain.RuleExecutionResult;
import com.example.disclosurereview.rule.domain.RuleExecutorType;
import com.example.disclosurereview.rule.domain.RuleScope;
import com.example.disclosurereview.rule.domain.RuleVersionStatus;
import com.example.disclosurereview.rule.domain.PlannedRuleReviewOutcome;
import com.example.disclosurereview.rule.domain.SemanticRuleCandidate;
import com.example.disclosurereview.rule.domain.SemanticRuleCheck;
import com.example.disclosurereview.rule.executor.RuleExecutor;
import com.example.disclosurereview.rule.executor.RuleExecutorRegistry;
import com.example.disclosurereview.rule.executor.RuleJsonSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
public class RuleEngine {

    private static final Logger log = LoggerFactory.getLogger(RuleEngine.class);
    private static final String NO_RULES_ENABLED = "__NO_RULES_ENABLED__";

    private final RuleEvaluator legacyEvaluator;
    private final ReviewRuleDefinitionJpaRepository ruleDefinitionRepository;
    private final ReviewRuleVersionJpaRepository ruleVersionRepository;
    private final ReviewRuleExecutionJpaRepository ruleExecutionRepository;
    private final ReviewTaskJpaRepository taskRepository;
    private final RuleExecutorRegistry executorRegistry;
    private final RuleJsonSupport json;

    @Autowired
    public RuleEngine(RuleEvaluator legacyEvaluator,
                      ReviewRuleDefinitionJpaRepository ruleDefinitionRepository,
                      ReviewRuleVersionJpaRepository ruleVersionRepository,
                      ReviewRuleExecutionJpaRepository ruleExecutionRepository,
                      ReviewTaskJpaRepository taskRepository,
                      RuleExecutorRegistry executorRegistry,
                      RuleJsonSupport json) {
        this.legacyEvaluator = legacyEvaluator;
        this.ruleDefinitionRepository = ruleDefinitionRepository;
        this.ruleVersionRepository = ruleVersionRepository;
        this.ruleExecutionRepository = ruleExecutionRepository;
        this.taskRepository = taskRepository;
        this.executorRegistry = executorRegistry;
        this.json = json;
    }

    public RuleEngine(RuleEvaluator ruleEvaluator,
                      ReviewRuleDefinitionJpaRepository ruleDefinitionRepository) {
        this(ruleEvaluator, ruleDefinitionRepository, null, null, null, null, null);
    }

    public RuleEngine(RuleReviewService ruleReviewService,
                      ReviewRuleDefinitionJpaRepository ruleDefinitionRepository) {
        this(new RuleEvaluator(ruleReviewService), ruleDefinitionRepository);
    }

    public RuleReviewService.RuleReviewOutcome review(List<DocumentPage> pages,
                                                      DocumentType documentType,
                                                      String declaredProductCode,
                                                      Product targetProduct) {
        return review(pages, null, documentType, null, declaredProductCode,
                null, null, targetProduct, null);
    }

    public RuleReviewService.RuleReviewOutcome review(List<DocumentPage> pages,
                                                      DocumentCategory documentCategory,
                                                      DocumentType documentType,
                                                      String fileName,
                                                      String declaredProductCode,
                                                      String declaredDocumentType,
                                                      String b9Value,
                                                      Product targetProduct,
                                                      Long taskId) {
        if (!templateEngineAvailable()) {
            return legacyReview(pages, documentType, declaredProductCode, targetProduct, Set.of());
        }

        RuleExecutionContext context = new RuleExecutionContext(taskId, pages, fileName,
                documentCategory, documentType, declaredProductCode, declaredDocumentType, b9Value, targetProduct);
        List<EvidenceValue> productCodes = new ArrayList<>();
        List<EvidenceValue> productNames = new ArrayList<>();
        List<ReviewIssue> issues = new ArrayList<>();
        Set<String> fallbackRuleCodes = new LinkedHashSet<>();

        try {
            List<ReviewRuleDefinitionEntity> definitions =
                    ruleDefinitionRepository.findByEnabledTrueOrderByPriorityDescRuleCodeAsc();
            if (definitions.isEmpty()) {
                return ruleDefinitionRepository.count() == 0
                        ? legacyReview(pages, documentType, declaredProductCode, targetProduct, Set.of())
                        : legacyReview(pages, documentType, declaredProductCode, targetProduct, Set.of(NO_RULES_ENABLED));
            }

            for (ReviewRuleDefinitionEntity definition : definitions) {
                if (!applies(definition, context)) {
                    continue;
                }
                Optional<ReviewRuleVersionEntity> version = activePublishedVersion(definition);
                if (version.isEmpty()) {
                    fallbackRuleCodes.add(definition.getRuleCode());
                    continue;
                }
                executeVersion(context, definition, version.get(), productCodes, productNames, issues);
            }

            if (!fallbackRuleCodes.isEmpty()) {
                RuleReviewService.RuleReviewOutcome fallback =
                        legacyReview(pages, documentType, declaredProductCode, targetProduct, fallbackRuleCodes);
                productCodes.addAll(fallback.productCodeCandidates());
                productNames.addAll(fallback.productNameCandidates());
                issues.addAll(fallback.issues());
            }
            return new RuleReviewService.RuleReviewOutcome(productCodes, productNames, List.of(), issues);
        } catch (Exception e) {
            log.warn("Rule template engine failed, falling back to built-in rules: {}", e.getMessage());
            return legacyReview(pages, documentType, declaredProductCode, targetProduct, Set.of());
        }
    }

    public PlannedRuleReviewOutcome reviewWithDeferredSemanticRules(List<DocumentPage> pages,
                                                                    DocumentCategory documentCategory,
                                                                    DocumentType documentType,
                                                                    String fileName,
                                                                    String declaredProductCode,
                                                                    String declaredDocumentType,
                                                                    String b9Value,
                                                                    Product targetProduct,
                                                                    Long taskId) {
        if (!templateEngineAvailable()) {
            return new PlannedRuleReviewOutcome(
                    legacyReview(pages, documentType, declaredProductCode, targetProduct, Set.of()),
                    List.of());
        }

        RuleExecutionContext context = new RuleExecutionContext(taskId, pages, fileName,
                documentCategory, documentType, declaredProductCode, declaredDocumentType, b9Value, targetProduct);
        List<EvidenceValue> productCodes = new ArrayList<>();
        List<EvidenceValue> productNames = new ArrayList<>();
        List<ReviewIssue> issues = new ArrayList<>();
        List<SemanticRuleCheck> semanticChecks = new ArrayList<>();
        Set<String> fallbackRuleCodes = new LinkedHashSet<>();

        try {
            List<ReviewRuleDefinitionEntity> definitions =
                    ruleDefinitionRepository.findByEnabledTrueOrderByPriorityDescRuleCodeAsc();
            if (definitions.isEmpty()) {
                RuleReviewService.RuleReviewOutcome fallback = ruleDefinitionRepository.count() == 0
                        ? legacyReview(pages, documentType, declaredProductCode, targetProduct, Set.of())
                        : legacyReview(pages, documentType, declaredProductCode, targetProduct, Set.of(NO_RULES_ENABLED));
                return new PlannedRuleReviewOutcome(fallback, List.of());
            }

            for (ReviewRuleDefinitionEntity definition : definitions) {
                if (!applies(definition, context)) {
                    continue;
                }
                Optional<ReviewRuleVersionEntity> version = activePublishedVersion(definition);
                if (version.isEmpty()) {
                    fallbackRuleCodes.add(definition.getRuleCode());
                    continue;
                }
                RuleExecutorType type = json.executorType(version.get());
                if (type == RuleExecutorType.LLM_POLICY || type == RuleExecutorType.HYBRID) {
                    planSemanticRule(context, definition, version.get(), type).ifPresent(semanticChecks::add);
                    continue;
                }
                executeVersion(context, definition, version.get(), productCodes, productNames, issues);
            }

            if (!fallbackRuleCodes.isEmpty()) {
                RuleReviewService.RuleReviewOutcome fallback =
                        legacyReview(pages, documentType, declaredProductCode, targetProduct, fallbackRuleCodes);
                productCodes.addAll(fallback.productCodeCandidates());
                productNames.addAll(fallback.productNameCandidates());
                issues.addAll(fallback.issues());
            }
            return new PlannedRuleReviewOutcome(
                    new RuleReviewService.RuleReviewOutcome(productCodes, productNames, List.of(), issues),
                    semanticChecks);
        } catch (Exception e) {
            log.warn("Rule planning failed, falling back to built-in rules: {}", e.getMessage());
            return new PlannedRuleReviewOutcome(
                    legacyReview(pages, documentType, declaredProductCode, targetProduct, Set.of()),
                    List.of());
        }
    }

    public void updateDeferredSemanticExecution(SemanticRuleCheck check,
                                                RuleExecutionResult result,
                                                long durationMs) {
        if (ruleExecutionRepository == null || check == null || check.executionId() == null || result == null) {
            return;
        }
        try {
            ruleExecutionRepository.findById(check.executionId()).ifPresent(entity -> {
                entity.setExecutionStatus(result.status().name());
                entity.setMatched(result.matched());
                entity.setIssueCount(result.issues().size());
                entity.setDurationMs(durationMs);
                entity.setErrorMessage(result.status().name().equals("FAILED") ? result.detail() : null);
                entity.setResultJson(json.toJson(result));
                entity.setEvidenceJson(json.toJson(result.evidence()));
                ruleExecutionRepository.save(entity);
            });
        } catch (Exception e) {
            log.debug("Failed to update deferred semantic rule execution: {}", e.getMessage());
        }
    }

    private boolean templateEngineAvailable() {
        return ruleDefinitionRepository != null
                && ruleVersionRepository != null
                && executorRegistry != null
                && json != null;
    }

    private Optional<SemanticRuleCheck> planSemanticRule(RuleExecutionContext context,
                                                        ReviewRuleDefinitionEntity definition,
                                                        ReviewRuleVersionEntity version,
                                                        RuleExecutorType type) {
        JsonNode condition = json.read(version.getConditionJson());
        List<SemanticRuleCandidate> candidates = switch (type) {
            case HYBRID -> hybridCandidates(context, json.text(condition, "locator", definition.getRuleCode()));
            case LLM_POLICY -> List.of();
            default -> List.of();
        };
        if (type == RuleExecutorType.HYBRID && candidates.isEmpty()) {
            persistExecution(context, definition, version, RuleExecutionResult.notHit(), 0);
            return Optional.empty();
        }
        Long executionId = persistExecution(context, definition, version,
                RuleExecutionResult.skipped("DEFERRED_TO_LLM_REVIEW"), 0);
        JsonNode prompt = json.read(version.getPromptJson());
        return Optional.of(new SemanticRuleCheck(
                definition.getId(),
                definition.getRuleCode(),
                version.getId(),
                version.getVersionCode(),
                executionId,
                type.name(),
                json.text(prompt, "reviewGoal", ""),
                json.text(prompt, "criteria", ""),
                json.text(prompt, "responseFormat", "JSON"),
                json.number(condition, "minConfidence", type == RuleExecutorType.HYBRID ? 0.8 : 0.75),
                json.action(definition, version),
                candidates));
    }

    private List<SemanticRuleCandidate> hybridCandidates(RuleExecutionContext context, String locator) {
        Set<String> enabled = switch (locator) {
            case RuleReviewService.RULE_CONTENT_PRODUCT_CODE_CONFLICT -> Set.of(
                    RuleReviewService.RULE_PRODUCT_CODE_EXTRACTION,
                    RuleReviewService.RULE_CONTENT_PRODUCT_CODE_CONFLICT);
            case RuleReviewService.RULE_POSSIBLE_TEMPLATE_RESIDUE -> Set.of(
                    RuleReviewService.RULE_PRODUCT_CODE_EXTRACTION,
                    RuleReviewService.RULE_POSSIBLE_TEMPLATE_RESIDUE);
            default -> Set.of(locator);
        };
        RuleReviewService.RuleReviewOutcome outcome = legacyReview(
                context.pages(), context.documentType(), context.declaredProductCode(), context.targetProduct(), enabled);
        return outcome.issues().stream()
                .map(issue -> new SemanticRuleCandidate(issue.pageNumber(), issue.evidenceText(), issue.explanation()))
                .toList();
    }

    private void executeVersion(RuleExecutionContext context,
                                ReviewRuleDefinitionEntity definition,
                                ReviewRuleVersionEntity version,
                                List<EvidenceValue> productCodes,
                                List<EvidenceValue> productNames,
                                List<ReviewIssue> issues) {
        RuleExecutorType type = json.executorType(version);
        RuleExecutor executor = executorRegistry.find(type).orElse(null);
        if (executor == null) {
            persistExecution(context, definition, version,
                    RuleExecutionResult.failed("Executor not registered: " + type), 0);
            return;
        }
        long started = System.nanoTime();
        RuleExecutionResult result;
        try {
            result = executor.execute(context, definition, version);
        } catch (Exception e) {
            result = RuleExecutionResult.failed(e.getMessage());
        }
        long durationMs = Math.max(0, (System.nanoTime() - started) / 1_000_000);
        Long executionId = persistExecution(context, definition, version, result, durationMs);
        productCodes.addAll(result.productCodeCandidates());
        productNames.addAll(result.productNameCandidates());
        issues.addAll(result.issues().stream()
                .map(issue -> issue.withRuleTrace(definition.getRuleCode(), version.getId(), executionId))
                .toList());
    }

    private Long persistExecution(RuleExecutionContext context,
                                  ReviewRuleDefinitionEntity definition,
                                  ReviewRuleVersionEntity version,
                                  RuleExecutionResult result,
                                  long durationMs) {
        if (ruleExecutionRepository == null || taskRepository == null || context.taskId() == null) {
            return null;
        }
        Optional<ReviewTaskEntity> task = taskRepository.findById(context.taskId());
        if (task.isEmpty()) {
            return null;
        }
        try {
            ReviewRuleExecutionEntity entity = new ReviewRuleExecutionEntity();
            entity.setTask(task.get());
            entity.setRuleCode(definition.getRuleCode());
            entity.setRuleVersion(version.getVersionCode());
            entity.setRuleId(definition.getId());
            entity.setRuleVersionId(version.getId());
            entity.setExecutionStatus(result.status().name());
            entity.setMatched(result.matched());
            entity.setIssueCount(result.issues().size());
            entity.setDurationMs(durationMs);
            entity.setErrorMessage(result.status().name().equals("FAILED") ? result.detail() : null);
            entity.setInputSnapshotJson(json.toJson(inputSnapshot(context)));
            entity.setResultJson(json.toJson(result));
            entity.setEvidenceJson(json.toJson(result.evidence()));
            entity.setCreatedAt(Instant.now());
            return ruleExecutionRepository.save(entity).getId();
        } catch (Exception e) {
            log.debug("Failed to persist rule execution: {}", e.getMessage());
            return null;
        }
    }

    private Object inputSnapshot(RuleExecutionContext context) {
        return java.util.Map.of(
                "taskId", context.taskId() == null ? "" : context.taskId(),
                "fileName", nullToEmpty(context.fileName()),
                "documentCategory", context.documentCategory() == null ? "" : context.documentCategory().name(),
                "documentType", context.documentType() == null ? "" : context.documentType().name(),
                "declaredProductCode", nullToEmpty(context.declaredProductCode()),
                "declaredDocumentType", nullToEmpty(context.declaredDocumentType()),
                "b9Value", nullToEmpty(context.b9Value()),
                "pageCount", context.pages().size());
    }

    private Optional<ReviewRuleVersionEntity> activePublishedVersion(ReviewRuleDefinitionEntity definition) {
        if (definition.getActiveVersionId() != null) {
            Optional<ReviewRuleVersionEntity> active = ruleVersionRepository.findById(definition.getActiveVersionId());
            if (active.isPresent() && isPublished(active.get())) {
                return active;
            }
        }
        return ruleVersionRepository
                .findByRuleDefinition_IdAndStatusOrderByVersionNumberDesc(
                        definition.getId(), RuleVersionStatus.PUBLISHED.name())
                .stream()
                .filter(ReviewRuleVersionEntity::isActive)
                .findFirst();
    }

    private boolean isPublished(ReviewRuleVersionEntity version) {
        return RuleVersionStatus.PUBLISHED.name().equalsIgnoreCase(version.getStatus())
                && version.isActive();
    }

    private boolean applies(ReviewRuleDefinitionEntity definition, RuleExecutionContext context) {
        Optional<ReviewRuleVersionEntity> version = activePublishedVersion(definition);
        if (version.isEmpty()) {
            return appliesToDocumentType(definition, context.documentType())
                    && appliesToProduct(definition, context.declaredProductCode(), context.targetProduct());
        }
        RuleScope scope = json.scope(version.get());
        return appliesToCategory(scope, context.documentCategory())
                && appliesToDocumentType(scope, context.documentType(), context.declaredDocumentType())
                && appliesToProduct(scope, context.declaredProductCode(), context.targetProduct());
    }

    private boolean appliesToCategory(RuleScope scope, DocumentCategory category) {
        if (scope.documentCategories().isEmpty()) {
            return true;
        }
        if (category == null) {
            return false;
        }
        for (String configured : scope.documentCategories()) {
            String normalized = normalizeCategory(configured);
            if (normalized.equals(category.name()) || ("AGREEMENT".equals(normalized) && category == DocumentCategory.PROTOCOL)) {
                return true;
            }
        }
        return false;
    }

    private String normalizeCategory(String value) {
        String normalized = value == null ? "" : value.strip().toUpperCase(Locale.ROOT);
        return "AGREEMENT".equals(normalized) ? "PROTOCOL" : normalized;
    }

    private boolean appliesToDocumentType(RuleScope scope, DocumentType documentType, String declaredDocumentType) {
        if (scope.documentTypes().isEmpty()) {
            return true;
        }
        for (String configured : scope.documentTypes()) {
            if (matchesDocumentType(configured, documentType)
                    || (StringUtils.hasText(declaredDocumentType) && containsIgnoreCase(declaredDocumentType, configured))) {
                return true;
            }
        }
        return false;
    }

    private boolean appliesToProduct(RuleScope scope, String declaredProductCode, Product product) {
        boolean codeMatches = scope.productCodes().isEmpty()
                || scope.productCodes().stream().anyMatch(code -> matchesProductCode(code, declaredProductCode, product));
        boolean typeMatches = scope.productTypes().isEmpty()
                || scope.productTypes().stream().anyMatch(type -> product != null && containsIgnoreCase(product.productType(), type));
        return codeMatches && typeMatches;
    }

    private boolean matchesProductCode(String configured, String declaredProductCode, Product product) {
        return containsIgnoreCase(declaredProductCode, configured)
                || (product != null && (containsIgnoreCase(product.productCode(), configured)
                || containsIgnoreCase(product.parentProductCode(), configured)
                || product.safeShareCodes().stream().anyMatch(code -> containsIgnoreCase(code, configured))));
    }

    private RuleReviewService.RuleReviewOutcome legacyReview(List<DocumentPage> pages,
                                                             DocumentType documentType,
                                                             String declaredProductCode,
                                                             Product targetProduct,
                                                             Set<String> enabledRuleCodes) {
        return legacyEvaluator.evaluate(pages, documentType, declaredProductCode, targetProduct, enabledRuleCodes);
    }

    private boolean appliesToDocumentType(ReviewRuleDefinitionEntity rule, DocumentType documentType) {
        String configured = rule.getDocumentTypes();
        if (configured == null || configured.isBlank() || "*".equals(configured.strip())) {
            return true;
        }
        if (documentType == null) {
            return false;
        }
        return matchesDocumentType(configured, documentType);
    }

    private boolean appliesToProduct(ReviewRuleDefinitionEntity rule, String declaredProductCode, Product product) {
        String configured = rule.getProductScope();
        if (configured == null || configured.isBlank() || "*".equals(configured.strip())) {
            return true;
        }
        String value = configured.toLowerCase(Locale.ROOT);
        if (StringUtils.hasText(declaredProductCode) && value.contains(declaredProductCode.toLowerCase(Locale.ROOT))) {
            return true;
        }
        if (product == null) {
            return false;
        }
        return contains(value, product.productCode())
                || contains(value, product.parentProductCode())
                || contains(value, product.productType());
    }

    private boolean matchesDocumentType(String configured, DocumentType documentType) {
        if (!StringUtils.hasText(configured) || documentType == null) {
            return false;
        }
        String value = configured.toLowerCase(Locale.ROOT);
        return value.contains(documentType.name().toLowerCase(Locale.ROOT))
                || value.contains(documentType.displayName().toLowerCase(Locale.ROOT));
    }

    private boolean containsIgnoreCase(String source, String candidate) {
        return StringUtils.hasText(source)
                && StringUtils.hasText(candidate)
                && source.toLowerCase(Locale.ROOT).contains(candidate.toLowerCase(Locale.ROOT));
    }

    private boolean contains(String source, String candidate) {
        return StringUtils.hasText(candidate) && source.contains(candidate.toLowerCase(Locale.ROOT));
    }

    private String nullToEmpty(Object value) {
        return Objects.toString(value, "");
    }
}
