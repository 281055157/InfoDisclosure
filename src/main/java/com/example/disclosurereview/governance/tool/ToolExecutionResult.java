package com.example.disclosurereview.governance.tool;

import com.fasterxml.jackson.databind.JsonNode;

public record ToolExecutionResult(JsonNode output, String candidateHash, Long proposalId) {
    public static ToolExecutionResult read(JsonNode output) { return new ToolExecutionResult(output, null, null); }
    public static ToolExecutionResult candidate(JsonNode output, String hash) { return new ToolExecutionResult(output, hash, null); }
    public static ToolExecutionResult proposal(JsonNode output, Long proposalId) { return new ToolExecutionResult(output, null, proposalId); }
}
