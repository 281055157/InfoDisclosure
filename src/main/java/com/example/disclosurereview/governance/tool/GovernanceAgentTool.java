package com.example.disclosurereview.governance.tool;

import com.fasterxml.jackson.databind.JsonNode;

public interface GovernanceAgentTool {
    String getName();
    String getDescription();
    JsonNode getInputSchema();
    Class<?> getInputType();
    boolean cacheable();
    default String cacheDiscriminator(JsonNode arguments, GovernanceToolExecutionContext context) { return ""; }
    default boolean parallelSafe() { return cacheable(); }
    ToolExecutionResult execute(JsonNode arguments, GovernanceToolExecutionContext context);
}
