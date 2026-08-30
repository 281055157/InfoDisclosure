package com.example.disclosurereview.rule.executor;

import com.example.disclosurereview.llm.EvidenceVerifier;
import com.example.disclosurereview.llm.LlmGateway;
import com.example.disclosurereview.model.ReviewIssue;
import com.example.disclosurereview.persistence.entity.ReviewRuleDefinitionEntity;
import com.example.disclosurereview.persistence.entity.ReviewRuleVersionEntity;
import com.example.disclosurereview.rule.domain.RuleAction;
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

@Component
public class LlmPolicyRuleExecutor implements RuleExecutor {

    private final LlmGateway llmGateway;
    private final EvidenceVerifier evidenceVerifier;
    private final RuleJsonSupport json;

    public LlmPolicyRuleExecutor(LlmGateway llmGateway, EvidenceVerifier evidenceVerifier, RuleJsonSupport json) {
        this.llmGateway = llmGateway;
        this.evidenceVerifier = evidenceVerifier;
        this.json = json;
    }

    @Override
    public RuleExecutorType supports() {
        return RuleExecutorType.LLM_POLICY;
    }

    @Override
    public RuleValidationResult validate(ReviewRuleVersionEntity version) {
        JsonNode prompt = json.read(version.getPromptJson());
        return StringUtils.hasText(json.text(prompt, "reviewGoal", null))
                ? RuleValidationResult.ok()
                : RuleValidationResult.invalid("candidateRule.prompt.reviewGoal is required");
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
        double minConfidence = json.number(condition, "minConfidence", 0.75);
        JsonNode response;
        try {
            response = llmGateway.chatCompletion("你是规则复核执行器。只返回 JSON。",
                    buildPrompt(context, definition, version, context.allText()),
                    json::stripAndReadJson);
        } catch (Exception e) {
            return RuleExecutionResult.indeterminate("LLM_POLICY_FAILED: " + e.getMessage());
        }
        return issueFromLlmResponse(context, definition, version, response, minConfidence, "LLM_POLICY");
    }

    @Override
    public Map<String, Object> schema() {
        return Map.of(
                "condition", Map.of("minConfidence", 0.75),
                "prompt", Map.of("reviewGoal", "审核目标", "criteria", "判定标准",
                        "responseFormat", "JSON with violated/confidence/pageNumber/evidenceText/explanation/suggestion"));
    }

    protected RuleExecutionResult issueFromLlmResponse(RuleExecutionContext context,
                                                       ReviewRuleDefinitionEntity definition,
                                                       ReviewRuleVersionEntity version,
                                                       JsonNode response,
                                                       double minConfidence,
                                                       String source) {
        response = normalizeResponse(response);
        if (response == null || response.isMissingNode() || response.isNull()
                || (!response.isValueNode() && response.size() == 0)) {
            return RuleExecutionResult.indeterminate("LLM_EMPTY_OR_INVALID_JSON");
        }
        if (!response.has("violated")) {
            return RuleExecutionResult.indeterminate("LLM_RESPONSE_MISSING_FIELD: violated");
        }
        boolean violated = booleanValue(response.path("violated"), false);
        double confidence = confidenceValue(response.path("confidence"));
        if (!violated) {
            return new RuleExecutionResult(
                    com.example.disclosurereview.rule.domain.RuleExecutionStatus.NOT_HIT,
                    false,
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    "LLM返回未违规，confidence=" + confidence);
        }
        if (confidence < minConfidence) {
            return RuleExecutionResult.indeterminate("LOW_CONFIDENCE: " + confidence + ", minConfidence=" + minConfidence);
        }
        Integer pageNumber = integerValue(response.path("pageNumber"));
        String evidenceText = text(response.path("evidenceText"));
        if (!evidenceVerifier.verifyText(pageNumber, evidenceText, context.pages())) {
            return RuleExecutionResult.indeterminate("EVIDENCE_NOT_VERIFIED: pageNumber="
                    + pageNumber + ", evidenceText=" + nullToEmpty(evidenceText));
        }
        RuleAction action = json.action(definition, version);
        String explanation = text(response.path("explanation"));
        String suggestion = text(response.path("suggestion"));
        ReviewIssue issue = new ReviewIssue(action.issueType(), action.severity(),
                Math.max(action.confidence(), confidence), pageNumber, evidenceText,
                StringUtils.hasText(explanation) ? explanation : json.render(action.explanationTemplate(), Map.of("detail", evidenceText)),
                StringUtils.hasText(suggestion) ? suggestion : json.render(action.suggestionTemplate(), Map.of("detail", evidenceText)),
                source,
                true);
        return RuleExecutionResult.hit(List.of(issue),
                List.of(new RuleEvidence(pageNumber, evidenceText, source, true)), "confidence=" + confidence);
    }

    private JsonNode normalizeResponse(JsonNode response) {
        if (response == null || response.isMissingNode() || response.isNull()) {
            return response;
        }
        if (response.has("violated")) {
            return response;
        }
        if (response.has("result") && response.path("result").has("violated")) {
            return response.path("result");
        }
        if (response.has("issue") && response.path("issue").has("violated")) {
            return response.path("issue");
        }
        if (response.has("issues") && response.path("issues").isArray() && response.path("issues").size() > 0) {
            JsonNode first = response.path("issues").get(0);
            if (first != null && first.has("violated")) {
                return first;
            }
        }
        return response;
    }

    protected String buildPrompt(RuleExecutionContext context,
                                 ReviewRuleDefinitionEntity definition,
                                 ReviewRuleVersionEntity version,
                                 String text) {
        JsonNode prompt = json.read(version.getPromptJson());
        return """
                规则编码：%s
                审核目标：%s
                判定标准：%s
                输出格式：%s
                文件名：%s
                文件类别：%s
                声明产品代码：%s
                声明文件类型：%s
                B9公告类型：%s
                正文：
                %s
                """.formatted(
                definition.getRuleCode(),
                json.text(prompt, "reviewGoal", ""),
                json.text(prompt, "criteria", ""),
                json.text(prompt, "responseFormat", "JSON"),
                nullToEmpty(context.fileName()),
                context.documentCategory() == null ? "" : context.documentCategory().name(),
                nullToEmpty(context.declaredProductCode()),
                nullToEmpty(context.declaredDocumentType()),
                nullToEmpty(context.b9Value()),
                text == null ? "" : text);
    }

    private String text(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? null : node.asText();
    }

    private boolean booleanValue(JsonNode node, boolean fallback) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return fallback;
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        String value = node.asText("");
        if ("true".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value) || "是".equals(value) || "违规".equals(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value) || "no".equalsIgnoreCase(value) || "否".equals(value) || "不违规".equals(value)) {
            return false;
        }
        return fallback;
    }

    private double confidenceValue(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return 0.0;
        }
        double value;
        if (node.isNumber()) {
            value = node.asDouble();
        } else {
            try {
                value = Double.parseDouble(node.asText("").replace("%", "").strip());
            } catch (Exception e) {
                return 0.0;
            }
        }
        if (value > 1.0 && value <= 100.0) {
            return value / 100.0;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }

    private Integer integerValue(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isInt()) {
            return node.asInt();
        }
        try {
            return Integer.parseInt(node.asText("").strip());
        } catch (Exception e) {
            return null;
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
