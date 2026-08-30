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
public class RegexRuleExecutor implements RuleExecutor {

    private static final int DEFAULT_PATTERN_LIMIT = 500;
    private static final int DEFAULT_INPUT_LIMIT = 50_000;
    private static final int DEFAULT_MAX_MATCHES = 20;
    private static final int DEFAULT_CONTEXT_RADIUS = 80;

    private final RuleJsonSupport json;

    public RegexRuleExecutor(RuleJsonSupport json) {
        this.json = json;
    }

    @Override
    public RuleExecutorType supports() {
        return RuleExecutorType.REGEX;
    }

    @Override
    public RuleValidationResult validate(ReviewRuleVersionEntity version) {
        JsonNode condition = json.read(version.getConditionJson());
        String pattern = json.text(condition, "pattern", null);
        int patternLimit = json.integer(condition, "maxPatternLength", DEFAULT_PATTERN_LIMIT);
        if (!StringUtils.hasText(pattern)) {
            return RuleValidationResult.invalid("condition.pattern is required");
        }
        if (pattern.length() > patternLimit) {
            return RuleValidationResult.invalid("pattern length exceeds " + patternLimit);
        }
        try {
            Pattern.compile(pattern);
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
        String patternText = json.text(condition, "pattern", "");
        Pattern pattern = Pattern.compile(patternText);
        int inputLimit = json.integer(condition, "maxInputLength", DEFAULT_INPUT_LIMIT);
        int maxMatches = json.integer(condition, "maxMatches", DEFAULT_MAX_MATCHES);
        int radius = json.integer(condition, "contextRadius", DEFAULT_CONTEXT_RADIUS);
        RuleAction action = json.action(definition, version);
        List<ReviewIssue> issues = new ArrayList<>();
        List<RuleEvidence> evidence = new ArrayList<>();
        int count = 0;
        for (DocumentPage page : context.pages()) {
            String pageText = page.normalizedText() == null ? "" : page.normalizedText();
            String text = pageText.length() > inputLimit ? pageText.substring(0, inputLimit) : pageText;
            Matcher matcher = pattern.matcher(text);
            while (matcher.find() && count < maxMatches) {
                String hit = json.context(text, matcher.start(), matcher.end(), radius);
                Map<String, String> vars = Map.of("match", matcher.group(), "detail", matcher.group());
                issues.add(json.issue(action, page.pageNumber(), hit, vars));
                evidence.add(new RuleEvidence(page.pageNumber(), hit, "REGEX", true));
                count++;
            }
            if (count >= maxMatches) {
                break;
            }
        }
        return issues.isEmpty()
                ? RuleExecutionResult.notHit()
                : RuleExecutionResult.hit(issues, evidence, "matches=" + count);
    }

    @Override
    public Map<String, Object> schema() {
        return Map.of(
                "condition", Map.of(
                        "pattern", "RE2/J regular expression",
                        "maxPatternLength", DEFAULT_PATTERN_LIMIT,
                        "maxInputLength", DEFAULT_INPUT_LIMIT,
                        "maxMatches", DEFAULT_MAX_MATCHES,
                        "contextRadius", DEFAULT_CONTEXT_RADIUS),
                "action", Map.of("issueType", "IssueType", "severity", "Severity",
                        "confidence", "0-1", "explanationTemplate", "supports ${match}", "suggestionTemplate", "text"));
    }
}
