package com.example.disclosurereview.rule.executor;

import com.example.disclosurereview.model.EvidenceValue;
import com.example.disclosurereview.model.ReviewIssue;
import com.example.disclosurereview.persistence.entity.ReviewRuleDefinitionEntity;
import com.example.disclosurereview.persistence.entity.ReviewRuleVersionEntity;
import com.example.disclosurereview.rule.RuleReviewService;
import com.example.disclosurereview.rule.domain.RuleEvidence;
import com.example.disclosurereview.rule.domain.RuleExecutionContext;
import com.example.disclosurereview.rule.domain.RuleExecutionResult;
import com.example.disclosurereview.rule.domain.RuleExecutorType;
import com.example.disclosurereview.rule.domain.RuleValidationResult;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class JavaPluginRuleExecutor implements RuleExecutor {

    private final RuleReviewService ruleReviewService;
    private final RuleJsonSupport json;

    public JavaPluginRuleExecutor(RuleReviewService ruleReviewService, RuleJsonSupport json) {
        this.ruleReviewService = ruleReviewService;
        this.json = json;
    }

    @Override
    public RuleExecutorType supports() {
        return RuleExecutorType.JAVA_PLUGIN;
    }

    @Override
    public RuleValidationResult validate(ReviewRuleVersionEntity version) {
        String pluginCode = pluginCode(version, null);
        return StringUtils.hasText(pluginCode)
                ? RuleValidationResult.ok()
                : RuleValidationResult.invalid("condition.pluginCode or ruleCode is required");
    }

    @Override
    public RuleExecutionResult execute(RuleExecutionContext context,
                                       ReviewRuleDefinitionEntity definition,
                                       ReviewRuleVersionEntity version) {
        String pluginCode = pluginCode(version, definition);
        if (RuleReviewService.RULE_PRODUCT_CODE_EXTRACTION.equals(pluginCode)) {
            List<EvidenceValue> codes = ruleReviewService.extractProductCodeCandidates(context.pages());
            return RuleExecutionResult.extraction(codes, List.of(), "codes=" + codes.size());
        }
        if (RuleReviewService.RULE_PRODUCT_NAME_EXTRACTION.equals(pluginCode)) {
            List<EvidenceValue> names = ruleReviewService.extractProductNameCandidates(context.pages());
            return RuleExecutionResult.extraction(List.of(), names, "names=" + names.size());
        }
        if (RuleReviewService.RULE_DECLARED_PRODUCT_NOT_FOUND.equals(pluginCode)) {
            RuleReviewService.RuleReviewOutcome outcome = ruleReviewService.review(
                    context.pages(), context.documentType(), context.declaredProductCode(), context.targetProduct(),
                    Set.of(RuleReviewService.RULE_DECLARED_PRODUCT_NOT_FOUND));
            return fromOutcome(outcome, "declared-product-not-found");
        }
        return RuleExecutionResult.skipped("Unknown Java plugin: " + pluginCode);
    }

    @Override
    public Map<String, Object> schema() {
        return Map.of("condition", Map.of("pluginCode", List.of(
                RuleReviewService.RULE_PRODUCT_CODE_EXTRACTION,
                RuleReviewService.RULE_PRODUCT_NAME_EXTRACTION,
                RuleReviewService.RULE_DECLARED_PRODUCT_NOT_FOUND)));
    }

    private String pluginCode(ReviewRuleVersionEntity version, ReviewRuleDefinitionEntity definition) {
        JsonNode condition = json.read(version == null ? null : version.getConditionJson());
        String value = json.text(condition, "pluginCode", null);
        return StringUtils.hasText(value) ? value : (definition == null ? null : definition.getRuleCode());
    }

    private RuleExecutionResult fromOutcome(RuleReviewService.RuleReviewOutcome outcome, String detail) {
        List<ReviewIssue> issues = outcome == null ? List.of() : outcome.issues();
        List<RuleEvidence> evidence = issues.stream()
                .map(i -> new RuleEvidence(i.pageNumber(), i.evidenceText(), "JAVA_PLUGIN", i.verified()))
                .toList();
        return issues.isEmpty()
                ? RuleExecutionResult.notHit()
                : RuleExecutionResult.hit(issues, evidence, detail);
    }
}
