package com.example.disclosurereview.rule.executor;

import com.example.disclosurereview.model.DocumentPage;
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

import java.util.List;
import java.util.Map;

@Component
public class RequiredRuleExecutor implements RuleExecutor {

    private final RuleJsonSupport json;

    public RequiredRuleExecutor(RuleJsonSupport json) {
        this.json = json;
    }

    @Override
    public RuleExecutorType supports() {
        return RuleExecutorType.REQUIRED;
    }

    @Override
    public RuleValidationResult validate(ReviewRuleVersionEntity version) {
        JsonNode condition = json.read(version.getConditionJson());
        List<String> values = json.strings(condition.path("values"));
        if (values.isEmpty()) {
            return RuleValidationResult.invalid("condition.values must contain at least one value");
        }
        return RuleValidationResult.ok();
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
        String mode = json.text(condition, "mode", "MUST_APPEAR");
        List<String> values = json.strings(condition.path("values"));
        String text = context.allText();
        boolean matched = switch (mode) {
            case "MUST_NOT_APPEAR" -> values.stream().anyMatch(text::contains);
            case "ANY_OF" -> values.stream().noneMatch(text::contains);
            default -> values.stream().anyMatch(v -> !text.contains(v));
        };
        if (!matched) {
            return RuleExecutionResult.notHit();
        }
        String detail = switch (mode) {
            case "MUST_NOT_APPEAR" -> "禁止出现字段已出现";
            case "ANY_OF" -> "要求至少出现一个字段但均未出现";
            default -> "要求字段未出现";
        };
        RuleAction action = json.action(definition, version);
        ReviewIssue issue = json.issue(action, firstPage(context), detail, Map.of("detail", detail));
        return RuleExecutionResult.hit(List.of(issue), List.of(new RuleEvidence(firstPage(context), detail, "REQUIRED", true)), detail);
    }

    @Override
    public Map<String, Object> schema() {
        return Map.of(
                "condition", Map.of("mode", "MUST_APPEAR | MUST_NOT_APPEAR | ANY_OF",
                        "values", List.of("keyword1", "keyword2")),
                "action", Map.of("issueType", "IssueType", "severity", "Severity",
                        "explanationTemplate", "supports ${detail}"));
    }

    private Integer firstPage(RuleExecutionContext context) {
        return context.pages().stream().map(DocumentPage::pageNumber).findFirst().orElse(null);
    }
}
