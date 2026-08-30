package com.example.disclosurereview.governance.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Component
public class GovernanceAgentResponseParser {
    private final ObjectMapper mapper;

    public GovernanceAgentResponseParser(ObjectMapper mapper) { this.mapper = mapper; }

    public AgentStep parse(String raw) {
        if (!StringUtils.hasText(raw)) throw new IllegalArgumentException("Agent 返回为空");
        String json = raw.strip();
        if (json.startsWith("```json")) json = json.substring(7);
        else if (json.startsWith("```")) json = json.substring(3);
        if (json.endsWith("```")) json = json.substring(0, json.length() - 3);
        try {
            JsonNode node = mapper.readTree(json.strip());
            String action = node.path("nextAction").asText();
            if (!"CALL_TOOL".equals(action) && !"CALL_TOOLS".equals(action) && !"FINISH".equals(action)) {
                throw new IllegalArgumentException("nextAction 必须是 CALL_TOOLS、CALL_TOOL 或 FINISH");
            }
            List<AgentToolCall> calls = new ArrayList<>();
            if (!"FINISH".equals(action)) {
                JsonNode batch = node.path("toolCalls");
                if (batch.isArray()) {
                    int index = 1;
                    for (JsonNode call : batch) {
                        String name = call.path("toolName").asText();
                        if (!StringUtils.hasText(name)) throw new IllegalArgumentException("toolCalls 缺少 toolName");
                        JsonNode arguments = call.path("arguments");
                        if (!arguments.isObject()) arguments = mapper.createObjectNode();
                        String callId = call.path("callId").asText("call-" + index);
                        calls.add(new AgentToolCall(callId, name, arguments));
                        index++;
                    }
                } else if (StringUtils.hasText(node.path("toolName").asText())) {
                    JsonNode arguments = node.path("arguments");
                    if (!arguments.isObject()) arguments = mapper.createObjectNode();
                    calls.add(new AgentToolCall("call-1", node.path("toolName").asText(), arguments));
                }
                if (calls.isEmpty()) throw new IllegalArgumentException(action + " 缺少 toolCalls/toolName");
            }
            return new AgentStep(node.path("thoughtSummary").asText(""), action, List.copyOf(calls), node);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Agent JSON 无效: " + e.getMessage(), e);
        }
    }

    public record AgentToolCall(String callId, String toolName, JsonNode arguments) {}
    public record AgentStep(String thoughtSummary, String nextAction, List<AgentToolCall> toolCalls,
                            JsonNode raw) {
        public String toolName() { return toolCalls.isEmpty() ? null : toolCalls.get(0).toolName(); }
        public JsonNode arguments() { return toolCalls.isEmpty() ? null : toolCalls.get(0).arguments(); }
    }
}
