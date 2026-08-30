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
import com.example.disclosurereview.util.TextNormalizer;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.re2j.Matcher;
import com.google.re2j.Pattern;
import com.google.re2j.PatternSyntaxException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class EnumMappingRuleExecutor implements RuleExecutor {

    private static final int DEFAULT_CONTEXT_LIMIT = 360;

    private final RuleJsonSupport json;

    public EnumMappingRuleExecutor(RuleJsonSupport json) {
        this.json = json;
    }

    @Override
    public RuleExecutorType supports() {
        return RuleExecutorType.ENUM_MAPPING;
    }

    @Override
    public RuleValidationResult validate(ReviewRuleVersionEntity version) {
        JsonNode condition = json.read(version.getConditionJson());
        String entryPattern = json.text(condition, "entryPattern", null);
        if (!StringUtils.hasText(entryPattern)) {
            return RuleValidationResult.invalid("condition.entryPattern is required");
        }
        if (json.stringMap(condition.path("expectedMapping")).isEmpty()) {
            return RuleValidationResult.invalid("condition.expectedMapping is required");
        }
        try {
            Pattern.compile(entryPattern);
            String headerPattern = json.text(condition, "headerPattern", null);
            if (StringUtils.hasText(headerPattern)) {
                Pattern.compile(headerPattern);
            }
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
        Pattern headerPattern = compileOptional(json.text(condition, "headerPattern", null));
        Pattern entryPattern = Pattern.compile(json.text(condition, "entryPattern", ""));
        int labelGroup = json.integer(condition, "labelGroup", 1);
        int codeGroup = json.integer(condition, "codeGroup", 2);
        boolean checkDuplicates = json.bool(condition, "checkDuplicates", true);
        boolean checkMissing = json.bool(condition, "checkMissing", false);
        boolean checkOrder = json.bool(condition, "checkOrder", false);
        Map<String, String> expected = json.stringMap(condition.path("expectedMapping"));
        RuleAction action = json.action(definition, version);
        List<ReviewIssue> issues = new ArrayList<>();
        List<RuleEvidence> evidences = new ArrayList<>();

        for (DocumentPage page : context.pages()) {
            String pageText = page.normalizedText() == null ? "" : page.normalizedText();
            String matchText = TextNormalizer.normalizeForMatch(pageText);
            List<Range> ranges = ranges(matchText, headerPattern);
            for (Range range : ranges) {
                String segment = matchText.substring(range.start(), range.end());
                EnumCheckResult checked = checkSegment(segment, entryPattern, labelGroup, codeGroup,
                        expected, checkDuplicates, checkMissing, checkOrder);
                if (!checked.details().isEmpty()) {
                    String evidence = evidenceText(pageText, range.start());
                    String detail = String.join("；", checked.details());
                    issues.add(json.issue(action, page.pageNumber(), evidence, Map.of("detail", detail)));
                    evidences.add(new RuleEvidence(page.pageNumber(), evidence, "ENUM_MAPPING", true));
                }
            }
        }
        return issues.isEmpty()
                ? RuleExecutionResult.notHit()
                : RuleExecutionResult.hit(issues, evidences, "issues=" + issues.size());
    }

    @Override
    public Map<String, Object> schema() {
        return Map.of(
                "condition", Map.of(
                        "headerPattern", "optional RE2/J regex to locate enum paragraph",
                        "entryPattern", "(label)(code) regex",
                        "labelGroup", 1,
                        "codeGroup", 2,
                        "expectedMapping", Map.of("R1", "低风险", "R2", "中低风险", "R3", "中风险"),
                        "checkDuplicates", true,
                        "checkMissing", true,
                        "checkOrder", true),
                "action", Map.of("issueType", "CONTENT_LOGIC_CONFLICT",
                        "explanationTemplate", "supports ${detail}"));
    }

    private Pattern compileOptional(String pattern) {
        return StringUtils.hasText(pattern) ? Pattern.compile(pattern) : null;
    }

    private List<Range> ranges(String text, Pattern headerPattern) {
        if (headerPattern == null) {
            return List.of(new Range(0, text.length()));
        }
        List<Range> result = new ArrayList<>();
        Matcher header = headerPattern.matcher(text);
        while (header.find()) {
            int end = firstSentenceEnd(text, header.end());
            result.add(new Range(header.start(), Math.min(end, text.length())));
        }
        return result;
    }

    private int firstSentenceEnd(String text, int from) {
        int period = text.indexOf('.', from);
        int semicolon = text.indexOf(';', from);
        if (period < 0) {
            return semicolon < 0 ? Math.min(text.length(), from + DEFAULT_CONTEXT_LIMIT) : semicolon;
        }
        if (semicolon < 0) {
            return period;
        }
        return Math.min(period, semicolon);
    }

    private EnumCheckResult checkSegment(String segment,
                                         Pattern entryPattern,
                                         int labelGroup,
                                         int codeGroup,
                                         Map<String, String> expected,
                                         boolean checkDuplicates,
                                         boolean checkMissing,
                                         boolean checkOrder) {
        Map<String, List<String>> seen = new LinkedHashMap<>();
        List<String> order = new ArrayList<>();
        Matcher entry = entryPattern.matcher(segment);
        while (entry.find()) {
            String code = normalizeCode(entry.group(codeGroup));
            String label = entry.group(labelGroup);
            seen.computeIfAbsent(code, ignored -> new ArrayList<>()).add(label);
            order.add(code);
        }
        List<String> details = new ArrayList<>();
        for (Map.Entry<String, List<String>> entrySet : seen.entrySet()) {
            String expectedLabel = expected.get(entrySet.getKey());
            if (expectedLabel == null) {
                continue;
            }
            for (String actual : entrySet.getValue()) {
                if (!expectedLabel.equals(actual)) {
                    details.add(entrySet.getKey() + "为" + actual + "，应为" + expectedLabel);
                }
            }
            if (checkDuplicates && entrySet.getValue().size() > 1) {
                details.add(entrySet.getKey() + "重复定义");
            }
        }
        if (checkMissing) {
            for (String code : expected.keySet()) {
                if (!seen.containsKey(code)) {
                    details.add(code + "缺失定义");
                }
            }
        }
        if (checkOrder && isOrderAbnormal(order)) {
            details.add("枚举顺序异常：" + String.join("、", order));
        }
        return new EnumCheckResult(details);
    }

    private boolean isOrderAbnormal(List<String> order) {
        int previous = 0;
        for (String code : order) {
            int current = parseLevel(code);
            if (current > 0 && previous > 0 && current < previous) {
                return true;
            }
            if (current > 0) {
                previous = current;
            }
        }
        return false;
    }

    private String normalizeCode(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String v = value.strip().toUpperCase();
        return v.startsWith("R") ? v : "R" + v;
    }

    private int parseLevel(String code) {
        try {
            return Integer.parseInt(code.replace("R", ""));
        } catch (Exception e) {
            return -1;
        }
    }

    private String evidenceText(String pageText, int normalizedStart) {
        String key = "风险程度";
        int start = pageText.indexOf(key);
        if (start < 0) {
            start = pageText.indexOf("风险等级");
        }
        if (start < 0) {
            start = Math.min(Math.max(0, normalizedStart), Math.max(0, pageText.length() - 1));
        }
        int end = pageText.indexOf('。', start);
        if (end < 0) {
            end = pageText.indexOf('.', start);
        }
        if (end < 0) {
            end = Math.min(pageText.length(), start + 260);
        } else {
            end = Math.min(pageText.length(), end + 1);
        }
        return pageText.substring(start, end).replaceAll("\\s+", " ").strip();
    }

    private record Range(int start, int end) {
    }

    private record EnumCheckResult(List<String> details) {
    }
}
