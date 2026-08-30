package com.example.disclosurereview.rule.executor;

import com.example.disclosurereview.model.IssueType;
import com.example.disclosurereview.model.ReviewIssue;
import com.example.disclosurereview.model.Severity;
import com.example.disclosurereview.persistence.entity.ReviewRuleDefinitionEntity;
import com.example.disclosurereview.persistence.entity.ReviewRuleVersionEntity;
import com.example.disclosurereview.rule.domain.RuleAction;
import com.example.disclosurereview.rule.domain.RuleExecutorType;
import com.example.disclosurereview.rule.domain.RuleScope;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class RuleJsonSupport {

    private static final JsonNode EMPTY = JsonNodeFactory.instance.objectNode();

    private final ObjectMapper objectMapper;

    public RuleJsonSupport(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public JsonNode read(String json) {
        if (!StringUtils.hasText(json)) {
            return EMPTY;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return EMPTY;
        }
    }

    public RuleScope scope(ReviewRuleVersionEntity version) {
        JsonNode root = read(version == null ? null : version.getScopeJson());
        return new RuleScope(strings(root.path("documentCategories")),
                strings(root.path("documentTypes")),
                strings(root.path("productCodes")),
                strings(root.path("productTypes")));
    }

    public RuleAction action(ReviewRuleDefinitionEntity definition, ReviewRuleVersionEntity version) {
        JsonNode root = read(version == null ? null : version.getActionJson());
        IssueType issueType = enumValue(IssueType.class, text(root, "issueType", null), null);
        Severity severity = enumValue(Severity.class, text(root, "severity", definition == null ? null : definition.getSeverity()), Severity.UNKNOWN);
        Double confidence = number(root, "confidence", definition == null ? null : definition.getConfidence());
        String explanation = text(root, "explanationTemplate", "规则命中。");
        String suggestion = text(root, "suggestionTemplate", "请人工确认。");
        String source = text(root, "source", "RULE");
        return new RuleAction(issueType == null ? IssueType.UNKNOWN_ISSUE : issueType,
                severity, confidence == null ? 0.5 : confidence, explanation, suggestion, source);
    }

    public ReviewIssue issue(RuleAction action,
                             Integer pageNumber,
                             String evidenceText,
                             Map<String, String> vars) {
        return new ReviewIssue(
                action.issueType(),
                action.severity(),
                action.confidence(),
                pageNumber,
                evidenceText,
                render(action.explanationTemplate(), vars),
                render(action.suggestionTemplate(), vars),
                action.source(),
                true);
    }

    public RuleExecutorType executorType(ReviewRuleVersionEntity version) {
        String value = version == null ? null : version.getExecutorType();
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return enumValue(RuleExecutorType.class, value, null);
    }

    public String text(JsonNode node, String field, String fallback) {
        JsonNode value = node == null ? null : node.path(field);
        if (value == null || value.isMissingNode() || value.isNull()) {
            return fallback;
        }
        String s = value.asText();
        return StringUtils.hasText(s) ? s.strip() : fallback;
    }

    public int integer(JsonNode node, String field, int fallback) {
        JsonNode value = node == null ? null : node.path(field);
        if (value == null || value.isMissingNode() || value.isNull() || !value.canConvertToInt()) {
            return fallback;
        }
        return value.asInt();
    }

    public long longValue(JsonNode node, String field, long fallback) {
        JsonNode value = node == null ? null : node.path(field);
        if (value == null || value.isMissingNode() || value.isNull() || !value.isNumber()) {
            return fallback;
        }
        return value.asLong();
    }

    public Double number(JsonNode node, String field, Double fallback) {
        JsonNode value = node == null ? null : node.path(field);
        if (value == null || value.isMissingNode() || value.isNull() || !value.isNumber()) {
            return fallback;
        }
        return value.asDouble();
    }

    public boolean bool(JsonNode node, String field, boolean fallback) {
        JsonNode value = node == null ? null : node.path(field);
        if (value == null || value.isMissingNode() || value.isNull() || !value.isBoolean()) {
            return fallback;
        }
        return value.asBoolean();
    }

    public List<String> strings(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        if (node.isArray()) {
            node.forEach(v -> addString(values, v.asText()));
        } else if (node.isTextual()) {
            for (String part : node.asText().split("[,，;；]")) {
                addString(values, part);
            }
        }
        return List.copyOf(values);
    }

    public Map<String, String> stringMap(JsonNode node) {
        if (node == null || !node.isObject()) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            result.put(field.getKey(), field.getValue().asText());
        }
        return result;
    }

    public String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    public Map<String, Object> readMap(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception e) {
            return Map.of();
        }
    }

    public JsonNode stripAndReadJson(String raw) {
        if (!StringUtils.hasText(raw)) {
            return EMPTY;
        }
        String json = raw.strip();
        if (json.startsWith("```json")) {
            json = json.substring(7);
        } else if (json.startsWith("```")) {
            json = json.substring(3);
        }
        if (json.endsWith("```")) {
            json = json.substring(0, json.length() - 3);
        }
        try {
            return objectMapper.readTree(json.strip());
        } catch (Exception e) {
            return EMPTY;
        }
    }

    public <T extends Enum<T>> T enumValue(Class<T> type, String value, T fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, value.strip().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return fallback;
        }
    }

    public String render(String template, Map<String, String> vars) {
        String rendered = template == null ? "" : template;
        if (vars == null) {
            return rendered;
        }
        for (Map.Entry<String, String> entry : vars.entrySet()) {
            rendered = rendered.replace("${" + entry.getKey() + "}", entry.getValue() == null ? "" : entry.getValue());
        }
        return rendered;
    }

    public String context(String text, int start, int end, int radius) {
        if (text == null || text.isBlank()) {
            return "";
        }
        int safeStart = Math.max(0, Math.min(start, text.length()));
        int safeEnd = Math.max(safeStart, Math.min(end, text.length()));
        int from = Math.max(0, safeStart - Math.max(radius, 0));
        int to = Math.min(text.length(), safeEnd + Math.max(radius, 0));
        String ctx = text.substring(from, to).replaceAll("\\s+", " ").strip();
        if (from > 0) {
            ctx = "…" + ctx;
        }
        if (to < text.length()) {
            ctx = ctx + "…";
        }
        return ctx;
    }

    private void addString(List<String> values, String value) {
        if (StringUtils.hasText(value)) {
            values.add(value.strip());
        }
    }
}
