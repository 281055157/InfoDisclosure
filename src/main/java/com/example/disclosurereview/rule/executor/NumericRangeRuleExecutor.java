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
import com.google.re2j.Matcher;
import com.google.re2j.Pattern;
import com.google.re2j.PatternSyntaxException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class NumericRangeRuleExecutor implements RuleExecutor {

    private final RuleJsonSupport json;

    public NumericRangeRuleExecutor(RuleJsonSupport json) {
        this.json = json;
    }

    @Override
    public RuleExecutorType supports() {
        return RuleExecutorType.NUMERIC_RANGE;
    }

    @Override
    public RuleValidationResult validate(ReviewRuleVersionEntity version) {
        JsonNode condition = json.read(version.getConditionJson());
        if (!StringUtils.hasText(json.text(condition, "pattern", null))) {
            return RuleValidationResult.invalid("condition.pattern is required");
        }
        try {
            Pattern.compile(json.text(condition, "pattern", ""));
            return RuleValidationResult.ok();
        } catch (PatternSyntaxException e) {
            return RuleValidationResult.invalid("RE2/J pattern invalid: " + e.getMessage());
        }
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
        Pattern pattern = Pattern.compile(json.text(condition, "pattern", ""));
        int valueGroup = json.integer(condition, "valueGroup", 1);
        Double min = json.number(condition, "min", null);
        Double max = json.number(condition, "max", null);
        RuleAction action = json.action(definition, version);
        List<ReviewIssue> issues = new ArrayList<>();
        List<RuleEvidence> evidence = new ArrayList<>();
        for (DocumentPage page : context.pages()) {
            String text = page.normalizedText() == null ? "" : page.normalizedText();
            Matcher matcher = pattern.matcher(text);
            while (matcher.find()) {
                Double value = parseNumber(matcher.group(valueGroup));
                if (value == null) {
                    continue;
                }
                boolean out = (min != null && value < min) || (max != null && value > max);
                if (out) {
                    String hit = json.context(text, matcher.start(), matcher.end(), 80);
                    Map<String, String> vars = Map.of("value", value.toString(), "detail", "数值 " + value + " 超出范围");
                    issues.add(json.issue(action, page.pageNumber(), hit, vars));
                    evidence.add(new RuleEvidence(page.pageNumber(), hit, "NUMERIC_RANGE", true));
                }
            }
        }
        return issues.isEmpty() ? RuleExecutionResult.notHit()
                : RuleExecutionResult.hit(issues, evidence, "issues=" + issues.size());
    }

    @Override
    public Map<String, Object> schema() {
        return Map.of("condition", Map.of("pattern", "RE2/J regex", "valueGroup", 1,
                "min", "optional number", "max", "optional number"));
    }

    private Double parseNumber(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Double.parseDouble(value.replace(",", "").replace("，", "").strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
