package com.example.disclosurereview.governance.service;

import com.example.disclosurereview.persistence.entity.ReviewRuleDefinitionEntity;
import com.example.disclosurereview.persistence.entity.ReviewRuleVersionEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

@Service
public class RuleSnapshotService {
    private final ObjectMapper mapper;

    public RuleSnapshotService(ObjectMapper mapper) { this.mapper = mapper; }

    public ObjectNode snapshot(ReviewRuleDefinitionEntity definition, ReviewRuleVersionEntity version) {
        ObjectNode root = mapper.createObjectNode();
        if (definition != null) {
            root.put("ruleDefinitionId", definition.getId());
            root.put("ruleCode", definition.getRuleCode());
            root.put("ruleName", definition.getRuleName());
            root.put("priority", definition.getPriority());
            root.put("enabled", definition.isEnabled());
            root.put("severity", definition.getSeverity());
            root.put("confidence", definition.getConfidence());
        }
        if (version != null) {
            root.put("ruleVersionId", version.getId());
            root.put("version", version.getVersionCode());
            root.put("versionNumber", version.getVersionNumber());
            root.put("executorType", version.getExecutorType());
            root.set("scope", read(version.getScopeJson()));
            root.set("condition", read(version.getConditionJson()));
            root.set("action", read(version.getActionJson()));
            root.set("prompt", read(version.getPromptJson()));
            root.put("status", version.getStatus());
            root.put("active", version.isActive());
        }
        return root;
    }

    private JsonNode read(String value) {
        try { return mapper.readTree(value == null || value.isBlank() ? "{}" : value); }
        catch (Exception e) { return mapper.createObjectNode(); }
    }
}
