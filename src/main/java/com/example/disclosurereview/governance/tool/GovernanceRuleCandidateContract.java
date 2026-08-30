package com.example.disclosurereview.governance.tool;

import com.example.disclosurereview.rule.domain.RuleExecutorType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;

public final class GovernanceRuleCandidateContract {
    private static final List<String> ALLOWED_EXECUTORS = List.of(
            "REGEX", "REQUIRED", "ENUM_MAPPING", "NUMERIC_RANGE", "LLM_POLICY", "HYBRID");

    private GovernanceRuleCandidateContract() {
    }

    public static ObjectNode schema(ObjectMapper mapper) {
        ObjectNode candidate = objectSchema(mapper, true);
        candidate.put("description", "完整候选规则。prompt 必须位于 candidateRule 根级，与 condition/action 同级；禁止使用 promptJson 或 condition.prompt");
        required(candidate, "ruleCode", "ruleName", "executorType", "scope", "condition", "action", "prompt", "priority", "enabled");
        ObjectNode properties = candidate.putObject("properties");
        properties.putObject("ruleCode").put("type", "string").put("description", "稳定且唯一的规则编码");
        properties.putObject("ruleName").put("type", "string");
        ObjectNode executor = properties.putObject("executorType").put("type", "string");
        ArrayNode executorValues = executor.putArray("enum");
        ALLOWED_EXECUTORS.forEach(executorValues::add);
        properties.set("scope", scopeSchema(mapper));
        properties.set("condition", conditionSchema(mapper));
        properties.set("action", actionSchema(mapper));
        properties.set("prompt", promptSchema(mapper));
        properties.putObject("priority").put("type", "integer");
        properties.putObject("enabled").put("type", "boolean");
        return candidate;
    }

    public static ObjectNode example(ObjectMapper mapper, RuleExecutorType executorType) {
        return executorType == RuleExecutorType.REGEX ? regexExample(mapper) : llmPolicyExample(mapper);
    }

    public static ObjectNode llmPolicyExample(ObjectMapper mapper) {
        ObjectNode candidate = baseExample(mapper, "NEW_LLM_CAPITAL_GUARANTEE", "禁止保本保收益表述（语义识别）", "LLM_POLICY");
        candidate.set("condition", mapper.createObjectNode().put("minConfidence", 0.8));
        candidate.set("prompt", mapper.createObjectNode()
                .put("reviewGoal", "识别正文中的正向保本、保证本金或保证收益承诺")
                .put("criteria", "正向承诺判定违规；非保本、不承诺、并不保证、无法保证等否定语境不违规")
                .put("responseFormat", "JSON with violated/confidence/pageNumber/evidenceText/explanation/suggestion"));
        return candidate;
    }

    public static ObjectNode regexExample(ObjectMapper mapper) {
        ObjectNode candidate = baseExample(mapper, "SOURCE_RULE_CODE", "源规则名称", "REGEX");
        candidate.set("condition", mapper.createObjectNode()
                .put("pattern", "保本保收益|保证本金|保证收益")
                .put("maxInputLength", 50000)
                .put("maxMatches", 10)
                .put("contextRadius", 80));
        candidate.set("prompt", mapper.createObjectNode());
        return candidate;
    }

    public static String promptPlacementHint() {
        return "LLM_POLICY 使用 candidateRule.prompt.reviewGoal；prompt 与 condition/action 同级。"
                + "不要使用 promptJson、condition.prompt 或 condition.prompt.promptJson。";
    }

    private static ObjectNode baseExample(ObjectMapper mapper, String ruleCode, String ruleName, String executorType) {
        ObjectNode candidate = mapper.createObjectNode();
        candidate.put("ruleCode", ruleCode);
        candidate.put("ruleName", ruleName);
        candidate.put("executorType", executorType);
        candidate.put("priority", 40);
        candidate.put("enabled", true);
        ObjectNode scope = candidate.putObject("scope");
        scope.putArray("documentCategories").add("PROTOCOL");
        scope.putArray("documentTypes");
        scope.putArray("productCodes");
        scope.putArray("productTypes");
        ObjectNode action = candidate.putObject("action");
        action.put("issueType", "CONTENT_LOGIC_CONFLICT");
        action.put("severity", "HIGH");
        action.put("confidence", 1.0);
        action.put("source", "RULE");
        action.put("explanationTemplate", "正文出现禁止性收益承诺表述：${detail}");
        action.put("suggestionTemplate", "请删除或修正保本、保证收益类表述。");
        return candidate;
    }

    private static ObjectNode scopeSchema(ObjectMapper mapper) {
        ObjectNode scope = objectSchema(mapper, false);
        required(scope, "documentCategories", "documentTypes", "productCodes", "productTypes");
        ObjectNode properties = scope.putObject("properties");
        ObjectNode categories = properties.putObject("documentCategories").put("type", "array");
        categories.putObject("items").put("type", "string");
        for (String field : List.of("documentTypes", "productCodes", "productTypes")) {
            ObjectNode array = properties.putObject(field).put("type", "array");
            array.putObject("items").put("type", "string");
        }
        return scope;
    }

    private static ObjectNode conditionSchema(ObjectMapper mapper) {
        ObjectNode condition = objectSchema(mapper, true);
        condition.put("description", "执行器条件。LLM_POLICY 通常只含 minConfidence；HYBRID 必须提供 locator 确定性定位规则编码；不要在此放 prompt");
        ObjectNode properties = condition.putObject("properties");
        properties.putObject("pattern").put("type", "string");
        properties.putObject("locator").put("type", "string");
        properties.putObject("minConfidence").put("type", "number");
        properties.putObject("maxPatternLength").put("type", "integer");
        properties.putObject("maxInputLength").put("type", "integer");
        properties.putObject("maxMatches").put("type", "integer");
        properties.putObject("contextRadius").put("type", "integer");
        return condition;
    }

    private static ObjectNode actionSchema(ObjectMapper mapper) {
        ObjectNode action = objectSchema(mapper, true);
        required(action, "issueType", "severity", "confidence", "source", "explanationTemplate", "suggestionTemplate");
        ObjectNode properties = action.putObject("properties");
        properties.putObject("issueType").put("type", "string");
        properties.putObject("severity").put("type", "string");
        properties.putObject("confidence").put("type", "number");
        properties.putObject("source").put("type", "string");
        properties.putObject("explanationTemplate").put("type", "string");
        properties.putObject("suggestionTemplate").put("type", "string");
        return action;
    }

    private static ObjectNode promptSchema(ObjectMapper mapper) {
        ObjectNode prompt = objectSchema(mapper, false);
        prompt.put("description", "根级提示词配置。executorType=LLM_POLICY 时 reviewGoal 必填，criteria 应为字符串");
        ObjectNode properties = prompt.putObject("properties");
        properties.putObject("reviewGoal").put("type", "string");
        properties.putObject("criteria").put("type", "string");
        properties.putObject("responseFormat").put("type", "string");
        return prompt;
    }

    private static ObjectNode objectSchema(ObjectMapper mapper, boolean additionalProperties) {
        return mapper.createObjectNode().put("type", "object").put("additionalProperties", additionalProperties);
    }

    private static void required(ObjectNode schema, String... fields) {
        ArrayNode required = schema.putArray("required");
        for (String field : fields) required.add(field);
    }
}
