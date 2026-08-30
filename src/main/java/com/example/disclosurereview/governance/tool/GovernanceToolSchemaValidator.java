package com.example.disclosurereview.governance.tool;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class GovernanceToolSchemaValidator {
    public List<String> validate(JsonNode schema, JsonNode arguments) {
        List<String> errors = new ArrayList<>();
        if (arguments == null || !arguments.isObject()) return List.of("arguments 必须是 JSON 对象");
        validateNode(schema, arguments, "", errors);
        return List.copyOf(errors);
    }

    private void validateNode(JsonNode schema, JsonNode value, String path, List<String> errors) {
        if (schema == null || schema.isMissingNode() || schema.isNull()) return;
        JsonNode required = schema.path("required");
        if (required != null && required.isArray()) required.forEach(field -> {
            String name = field.asText();
            if (!value.has(name) || value.path(name).isNull()) errors.add(childPath(path, name) + " is required");
        });
        JsonNode properties = schema.path("properties");
        if (value.isObject() && properties.isObject()) {
            value.fields().forEachRemaining(entry -> {
                String fieldPath = childPath(path, entry.getKey());
                JsonNode definition = properties.path(entry.getKey());
                if (definition.isMissingNode()) {
                    if (schema.path("additionalProperties").isBoolean() && !schema.path("additionalProperties").asBoolean()) {
                        errors.add("unknown field: " + fieldPath);
                    }
                    return;
                }
                String type = definition.path("type").asText();
                JsonNode fieldValue = entry.getValue();
                if (!fieldValue.isNull() && !matches(type, fieldValue)) {
                    errors.add(fieldPath + " type must be " + type);
                    return;
                }
                if (definition.path("enum").isArray() && !contains(definition.path("enum"), fieldValue.asText())) {
                    errors.add(fieldPath + " has unsupported value: " + fieldValue.asText());
                }
                validateNode(definition, fieldValue, fieldPath, errors);
            });
        }
        if (value.isArray() && schema.path("items").isObject()) {
            for (int i = 0; i < value.size(); i++) {
                validateNode(schema.path("items"), value.path(i), path + "[" + i + "]", errors);
            }
        }
    }

    private boolean matches(String type, JsonNode value) {
        return switch (type) {
            case "string" -> value.isTextual();
            case "integer" -> value.isIntegralNumber();
            case "number" -> value.isNumber();
            case "boolean" -> value.isBoolean();
            case "array" -> value.isArray();
            case "object" -> value.isObject();
            case "" -> true;
            default -> true;
        };
    }

    private boolean contains(JsonNode array, String value) {
        for (JsonNode item : array) if (item.asText().equals(value)) return true;
        return false;
    }

    private String childPath(String parent, String child) {
        return parent == null || parent.isBlank() ? child : parent + "." + child;
    }
}
