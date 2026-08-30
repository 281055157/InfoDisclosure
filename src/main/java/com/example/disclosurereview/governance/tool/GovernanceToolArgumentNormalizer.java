package com.example.disclosurereview.governance.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class GovernanceToolArgumentNormalizer {
    private final ObjectMapper mapper;

    public GovernanceToolArgumentNormalizer(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public NormalizationResult normalize(JsonNode arguments) {
        if (!(arguments instanceof ObjectNode object)) return new NormalizationResult(arguments, List.of());
        ObjectNode normalized = object.deepCopy();
        List<String> repairs = new ArrayList<>();
        normalizeCandidate(normalized.path("candidateRule"), "candidateRule", repairs);
        if (normalized.path("actions") instanceof ArrayNode actions) {
            for (int i = 0; i < actions.size(); i++) {
                normalizeCandidate(actions.path(i).path("candidateRule"), "actions[" + i + "].candidateRule", repairs);
            }
        }
        return new NormalizationResult(normalized, List.copyOf(repairs));
    }

    private void normalizeCandidate(JsonNode value, String path, List<String> repairs) {
        if (!(value instanceof ObjectNode candidate)) return;
        ObjectNode condition = candidate.path("condition") instanceof ObjectNode object ? object : null;
        JsonNode rootPrompt = candidate.get("prompt");
        JsonNode source = useful(rootPrompt) ? rootPrompt : firstUseful(
                candidate.get("promptJson"),
                condition == null ? null : condition.get("prompt"),
                condition == null ? null : condition.get("promptJson"));
        ObjectNode prompt = promptObject(source);
        if (prompt != null) {
            boolean moved = source != rootPrompt || !rootPromptIsCanonical(rootPrompt);
            normalizeCriteria(prompt, path, repairs);
            candidate.set("prompt", prompt);
            candidate.remove("promptJson");
            if (condition != null) {
                condition.remove("prompt");
                condition.remove("promptJson");
            }
            if (moved) repairs.add(path + ".prompt 已规范化为根级字段");
        }
    }

    private ObjectNode promptObject(JsonNode value) {
        JsonNode parsed = parse(value);
        if (!(parsed instanceof ObjectNode object)) return null;
        JsonNode nested = firstUseful(object.get("promptJson"), object.get("prompt"));
        if (!useful(object.get("reviewGoal")) && nested != null) {
            JsonNode unwrapped = parse(nested);
            if (unwrapped instanceof ObjectNode nestedObject) return nestedObject.deepCopy();
        }
        return object.deepCopy();
    }

    private void normalizeCriteria(ObjectNode prompt, String path, List<String> repairs) {
        if (!(prompt.path("criteria") instanceof ArrayNode criteria)) return;
        List<String> values = new ArrayList<>();
        criteria.forEach(item -> {
            if (item.isValueNode() && !item.asText().isBlank()) values.add(item.asText());
        });
        prompt.put("criteria", String.join("；", values));
        repairs.add(path + ".prompt.criteria 已由数组规范化为字符串");
    }

    private JsonNode parse(JsonNode value) {
        if (value == null || value.isNull() || value.isMissingNode()) return null;
        if (!value.isTextual()) return value;
        try {
            return mapper.readTree(value.asText());
        } catch (Exception ignored) {
            return value;
        }
    }

    private JsonNode firstUseful(JsonNode... values) {
        for (JsonNode value : values) if (useful(value)) return value;
        return null;
    }

    private boolean useful(JsonNode value) {
        return value != null && !value.isNull() && !value.isMissingNode()
                && (!(value.isObject() || value.isArray() || value.isTextual()) || !value.isEmpty());
    }

    private boolean rootPromptIsCanonical(JsonNode value) {
        return value instanceof ObjectNode object
                && !object.has("promptJson") && !object.has("prompt");
    }

    public record NormalizationResult(JsonNode arguments, List<String> repairs) {
    }
}
