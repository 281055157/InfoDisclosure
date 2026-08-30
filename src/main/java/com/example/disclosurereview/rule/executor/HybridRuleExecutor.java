package com.example.disclosurereview.rule.executor;

import com.example.disclosurereview.llm.EvidenceVerifier;
import com.example.disclosurereview.llm.LlmGateway;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class HybridRuleExecutor implements RuleExecutor {

    private final RuleReviewService ruleReviewService;
    private final LlmGateway llmGateway;
    private final EvidenceVerifier evidenceVerifier;
    private final RuleJsonSupport json;

    public HybridRuleExecutor(RuleReviewService ruleReviewService,
                              LlmGateway llmGateway,
                              EvidenceVerifier evidenceVerifier,
                              RuleJsonSupport json) {
        this.ruleReviewService = ruleReviewService;
        this.llmGateway = llmGateway;
        this.evidenceVerifier = evidenceVerifier;
        this.json = json;
    }

    @Override
    public RuleExecutorType supports() {
        return RuleExecutorType.HYBRID;
    }

    @Override
    public RuleValidationResult validate(ReviewRuleVersionEntity version) {
        JsonNode condition = json.read(version.getConditionJson());
        String locator = json.text(condition, "locator", null);
        return StringUtils.hasText(locator)
                ? RuleValidationResult.ok()
                : RuleValidationResult.invalid("condition.locator is required");
    }

    @Override
    public RuleExecutionResult execute(RuleExecutionContext context,
                                       ReviewRuleDefinitionEntity definition,
                                       ReviewRuleVersionEntity version) {
        RuleValidationResult validation = validate(version);
        if (!validation.valid()) {
            return RuleExecutionResult.failed(String.join("; ", validation.errors()));
        }
        JsonNode condition = json.read(version.getConditionJson());
        String locator = json.text(condition, "locator", definition.getRuleCode());
        double minConfidence = json.number(condition, "minConfidence", 0.8);
        List<ReviewIssue> candidates = locateCandidates(context, locator);
        if (candidates.isEmpty()) {
            return RuleExecutionResult.notHit();
        }

        List<ReviewIssue> confirmed = new ArrayList<>();
        List<RuleEvidence> evidence = new ArrayList<>();
        List<String> degraded = new ArrayList<>();
        for (ReviewIssue candidate : candidates) {
            JsonNode response;
            try {
                response = llmGateway.chatCompletion("你是规则复核执行器。只返回 JSON。",
                        buildPrompt(context, definition, version, candidate),
                        json::stripAndReadJson);
            } catch (Exception e) {
                degraded.add("LLM_FAILED: " + e.getMessage());
                continue;
            }
            RuleExecutionResult checked = issueFromResponse(context, definition, version, response, minConfidence);
            if (!checked.issues().isEmpty()) {
                confirmed.addAll(checked.issues());
                evidence.addAll(checked.evidence());
            } else if (checked.status().name().equals("INDETERMINATE")) {
                degraded.add(checked.detail());
            }
        }
        if (confirmed.isEmpty()) {
            return degraded.isEmpty()
                    ? RuleExecutionResult.notHit()
                    : RuleExecutionResult.indeterminate(String.join("; ", degraded));
        }
        return RuleExecutionResult.hit(confirmed, evidence, "confirmed=" + confirmed.size());
    }

    @Override
    public Map<String, Object> schema() {
        return Map.of(
                "condition", Map.of("locator", List.of(
                        RuleReviewService.RULE_CONTENT_PRODUCT_CODE_CONFLICT,
                        RuleReviewService.RULE_POSSIBLE_TEMPLATE_RESIDUE), "minConfidence", 0.8),
                "prompt", Map.of("reviewGoal", "复核硬规则候选段落", "criteria", "判定标准"));
    }

    private List<ReviewIssue> locateCandidates(RuleExecutionContext context, String locator) {
        Set<String> enabled = switch (locator) {
            case RuleReviewService.RULE_CONTENT_PRODUCT_CODE_CONFLICT -> Set.of(
                    RuleReviewService.RULE_PRODUCT_CODE_EXTRACTION,
                    RuleReviewService.RULE_CONTENT_PRODUCT_CODE_CONFLICT);
            case RuleReviewService.RULE_POSSIBLE_TEMPLATE_RESIDUE -> Set.of(
                    RuleReviewService.RULE_PRODUCT_CODE_EXTRACTION,
                    RuleReviewService.RULE_POSSIBLE_TEMPLATE_RESIDUE);
            default -> Set.of(locator);
        };
        RuleReviewService.RuleReviewOutcome outcome = ruleReviewService.review(
                context.pages(), context.documentType(), context.declaredProductCode(), context.targetProduct(), enabled);
        return outcome.issues();
    }

    private RuleExecutionResult issueFromResponse(RuleExecutionContext context,
                                                  ReviewRuleDefinitionEntity definition,
                                                  ReviewRuleVersionEntity version,
                                                  JsonNode response,
                                                  double minConfidence) {
        boolean violated = response.path("violated").asBoolean(false);
        double confidence = response.path("confidence").isNumber() ? response.path("confidence").asDouble() : 0.0;
        if (!violated) {
            return RuleExecutionResult.notHit();
        }
        if (confidence < minConfidence) {
            return RuleExecutionResult.indeterminate("LOW_CONFIDENCE: " + confidence);
        }
        Integer pageNumber = response.path("pageNumber").isInt() ? response.path("pageNumber").asInt() : null;
        String evidenceText = response.path("evidenceText").asText(null);
        if (!evidenceVerifier.verifyText(pageNumber, evidenceText, context.pages())) {
            return RuleExecutionResult.indeterminate("EVIDENCE_NOT_VERIFIED");
        }
        var action = json.action(definition, version);
        String explanation = response.path("explanation").asText(null);
        String suggestion = response.path("suggestion").asText(null);
        ReviewIssue issue = new ReviewIssue(action.issueType(), action.severity(),
                Math.max(action.confidence(), confidence), pageNumber, evidenceText,
                StringUtils.hasText(explanation) ? explanation : json.render(action.explanationTemplate(), Map.of("llmExplanation", "")),
                StringUtils.hasText(suggestion) ? suggestion : json.render(action.suggestionTemplate(), Map.of()),
                action.source(),
                true);
        return RuleExecutionResult.hit(List.of(issue),
                List.of(new RuleEvidence(pageNumber, evidenceText, "HYBRID", true)),
                "confidence=" + confidence);
    }

    private String buildPrompt(RuleExecutionContext context,
                               ReviewRuleDefinitionEntity definition,
                               ReviewRuleVersionEntity version,
                               ReviewIssue candidate) {
        JsonNode prompt = json.read(version.getPromptJson());
        return """
                规则编码：%s
                审核目标：%s
                判定标准：%s
                文件名：%s
                声明产品代码：%s
                声明文件类型：%s
                候选页码：%s
                候选证据：%s
                候选说明：%s
                输出 JSON 字段：violated, confidence, pageNumber, evidenceText, explanation, suggestion
                """.formatted(
                definition.getRuleCode(),
                json.text(prompt, "reviewGoal", ""),
                json.text(prompt, "criteria", ""),
                nullToEmpty(context.fileName()),
                nullToEmpty(context.declaredProductCode()),
                nullToEmpty(context.declaredDocumentType()),
                candidate.pageNumber() == null ? "" : candidate.pageNumber(),
                nullToEmpty(candidate.evidenceText()),
                nullToEmpty(candidate.explanation()));
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
