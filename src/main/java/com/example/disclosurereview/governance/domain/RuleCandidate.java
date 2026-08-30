package com.example.disclosurereview.governance.domain;

import com.example.disclosurereview.persistence.entity.ReviewRuleDefinitionEntity;
import com.example.disclosurereview.persistence.entity.ReviewRuleVersionEntity;
import com.example.disclosurereview.rule.domain.RuleExecutorType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.springframework.util.StringUtils;

public record RuleCandidate(
        String ruleCode,
        String ruleName,
        RuleExecutorType executorType,
        JsonNode scope,
        JsonNode condition,
        JsonNode action,
        JsonNode prompt,
        Integer priority,
        Boolean enabled
) {
    public static RuleCandidate from(JsonNode node, ObjectMapper mapper) {
        JsonNode empty = JsonNodeFactory.instance.objectNode();
        String type = text(node, "executorType");
        RuleExecutorType executor = null;
        try {
            if (StringUtils.hasText(type)) executor = RuleExecutorType.valueOf(type.strip().toUpperCase());
        } catch (Exception ignored) {
        }
        return new RuleCandidate(text(node, "ruleCode"), text(node, "ruleName"), executor,
                object(node, "scope", mapper, empty), object(node, "condition", mapper, empty),
                object(node, "action", mapper, empty), object(node, "prompt", mapper, empty),
                node != null && node.path("priority").canConvertToInt() ? node.path("priority").asInt() : 100,
                node == null || !node.has("enabled") || node.path("enabled").asBoolean(true));
    }

    public ReviewRuleDefinitionEntity definition() {
        ReviewRuleDefinitionEntity entity = new ReviewRuleDefinitionEntity();
        entity.setRuleCode(ruleCode);
        entity.setRuleName(StringUtils.hasText(ruleName) ? ruleName : ruleCode);
        entity.setRuleType(executorType == null ? "" : executorType.name());
        entity.setRuleCategory(executorType == null ? "" : executorType.name());
        entity.setPriority(priority == null ? 100 : priority);
        entity.setEnabled(Boolean.TRUE.equals(enabled));
        entity.setVersionCode("candidate");
        return entity;
    }

    public ReviewRuleVersionEntity version(ObjectMapper mapper) {
        ReviewRuleVersionEntity entity = new ReviewRuleVersionEntity();
        entity.setVersionCode("candidate");
        entity.setVersionNumber(0);
        entity.setExecutorType(executorType == null ? null : executorType.name());
        entity.setScopeJson(json(mapper, scope));
        entity.setConditionJson(json(mapper, condition));
        entity.setActionJson(json(mapper, action));
        entity.setPromptJson(json(mapper, prompt));
        entity.setStatus("DRAFT");
        entity.setActive(false);
        return entity;
    }

    private static JsonNode object(JsonNode root, String field, ObjectMapper mapper, JsonNode empty) {
        if (root == null) return empty;
        JsonNode value = root.path(field);
        if (value.isMissingNode() || value.isNull()) return empty;
        if (value.isObject()) return value;
        if (value.isTextual()) {
            try {
                JsonNode parsed = mapper.readTree(value.asText());
                return parsed;
            } catch (Exception ignored) {
                return value;
            }
        }
        return value;
    }

    private static String text(JsonNode root, String field) {
        if (root == null || root.path(field).isMissingNode() || root.path(field).isNull()) return null;
        return root.path(field).asText(null);
    }

    private static String json(ObjectMapper mapper, JsonNode value) {
        try { return mapper.writeValueAsString(value == null ? JsonNodeFactory.instance.objectNode() : value); }
        catch (Exception e) { return "{}"; }
    }
}
