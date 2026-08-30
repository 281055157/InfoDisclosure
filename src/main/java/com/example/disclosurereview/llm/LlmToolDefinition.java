package com.example.disclosurereview.llm;

import com.fasterxml.jackson.databind.JsonNode;

public record LlmToolDefinition(String name, String description, JsonNode inputSchema) {}
