package com.example.disclosurereview.rule;

import com.example.disclosurereview.model.DocumentCategory;
import com.example.disclosurereview.model.DocumentType;
import com.example.disclosurereview.model.Product;
import com.example.disclosurereview.persistence.entity.ReviewRuleDefinitionEntity;
import com.example.disclosurereview.persistence.entity.ReviewRuleVersionEntity;
import com.example.disclosurereview.persistence.repository.ReviewRuleDefinitionJpaRepository;
import com.example.disclosurereview.persistence.repository.ReviewRuleVersionJpaRepository;
import com.example.disclosurereview.rule.domain.RuleExecutorType;
import com.example.disclosurereview.rule.domain.RuleScope;
import com.example.disclosurereview.rule.domain.RuleVersionStatus;
import com.example.disclosurereview.rule.executor.RuleJsonSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
public class PromptPolicyProvider {

    private final ReviewRuleDefinitionJpaRepository definitionRepository;
    private final ReviewRuleVersionJpaRepository versionRepository;
    private final RuleJsonSupport json;

    public PromptPolicyProvider(ReviewRuleDefinitionJpaRepository definitionRepository,
                                ReviewRuleVersionJpaRepository versionRepository,
                                RuleJsonSupport json) {
        this.definitionRepository = definitionRepository;
        this.versionRepository = versionRepository;
        this.json = json;
    }

    private PromptPolicyProvider() {
        this.definitionRepository = null;
        this.versionRepository = null;
        this.json = null;
    }

    public static PromptPolicyProvider disabled() {
        return new PromptPolicyProvider();
    }

    public String additionalPolicy(DocumentCategory category,
                                   DocumentType documentType,
                                   String declaredProductCode,
                                   String declaredDocumentType,
                                   Product product) {
        if (definitionRepository == null || versionRepository == null || json == null) {
            return "";
        }
        try {
            List<ReviewRuleDefinitionEntity> definitions =
                    definitionRepository.findByEnabledTrueOrderByPriorityDescRuleCodeAsc();
            StringBuilder sb = new StringBuilder();
            for (ReviewRuleDefinitionEntity definition : definitions) {
                Optional<ReviewRuleVersionEntity> version = activePublished(definition);
                if (version.isEmpty()) {
                    continue;
                }
                RuleExecutorType type = json.executorType(version.get());
                if (type != RuleExecutorType.LLM_POLICY) {
                    continue;
                }
                RuleScope scope = json.scope(version.get());
                if (!applies(scope, category, documentType, declaredProductCode, declaredDocumentType, product)) {
                    continue;
                }
                JsonNode prompt = json.read(version.get().getPromptJson());
                sb.append("\n动态规则：").append(definition.getRuleCode())
                        .append("\n审核目标：").append(json.text(prompt, "reviewGoal", ""))
                        .append("\n判定标准：").append(json.text(prompt, "criteria", ""))
                        .append("\n输出要求：如发现该动态规则命中，请在 issues 中返回对应证据；证据必须来自原文。")
                        .append('\n');
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private Optional<ReviewRuleVersionEntity> activePublished(ReviewRuleDefinitionEntity definition) {
        if (definition.getActiveVersionId() != null) {
            Optional<ReviewRuleVersionEntity> version = versionRepository.findById(definition.getActiveVersionId());
            if (version.isPresent() && isPublished(version.get())) {
                return version;
            }
        }
        return versionRepository.findByRuleDefinition_IdAndStatusOrderByVersionNumberDesc(
                        definition.getId(), RuleVersionStatus.PUBLISHED.name())
                .stream()
                .filter(ReviewRuleVersionEntity::isActive)
                .findFirst();
    }

    private boolean isPublished(ReviewRuleVersionEntity version) {
        return version.isActive() && RuleVersionStatus.PUBLISHED.name().equalsIgnoreCase(version.getStatus());
    }

    private boolean applies(RuleScope scope,
                            DocumentCategory category,
                            DocumentType documentType,
                            String declaredProductCode,
                            String declaredDocumentType,
                            Product product) {
        return appliesCategory(scope, category)
                && appliesDocumentType(scope, documentType, declaredDocumentType)
                && appliesProduct(scope, declaredProductCode, product);
    }

    private boolean appliesCategory(RuleScope scope, DocumentCategory category) {
        if (scope.documentCategories().isEmpty()) {
            return true;
        }
        if (category == null) {
            return false;
        }
        return scope.documentCategories().stream()
                .map(v -> "AGREEMENT".equalsIgnoreCase(v) ? "PROTOCOL" : v)
                .anyMatch(v -> v.equalsIgnoreCase(category.name()));
    }

    private boolean appliesDocumentType(RuleScope scope, DocumentType documentType, String declaredDocumentType) {
        if (scope.documentTypes().isEmpty()) {
            return true;
        }
        return scope.documentTypes().stream().anyMatch(v ->
                documentType != null && (v.equalsIgnoreCase(documentType.name()) || v.equalsIgnoreCase(documentType.displayName()))
                        || containsIgnoreCase(declaredDocumentType, v));
    }

    private boolean appliesProduct(RuleScope scope, String declaredProductCode, Product product) {
        boolean code = scope.productCodes().isEmpty()
                || scope.productCodes().stream().anyMatch(v -> containsIgnoreCase(declaredProductCode, v)
                || product != null && (containsIgnoreCase(product.productCode(), v)
                || containsIgnoreCase(product.parentProductCode(), v)));
        boolean type = scope.productTypes().isEmpty()
                || scope.productTypes().stream().anyMatch(v -> product != null && containsIgnoreCase(product.productType(), v));
        return code && type;
    }

    private boolean containsIgnoreCase(String source, String candidate) {
        return StringUtils.hasText(source) && StringUtils.hasText(candidate)
                && source.toLowerCase(Locale.ROOT).contains(candidate.toLowerCase(Locale.ROOT));
    }
}
