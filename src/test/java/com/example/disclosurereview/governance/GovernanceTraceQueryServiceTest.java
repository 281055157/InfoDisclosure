package com.example.disclosurereview.governance;

import com.example.disclosurereview.governance.domain.GovernanceRunStatus;
import com.example.disclosurereview.governance.persistence.entity.RuleGovernanceRunEntity;
import com.example.disclosurereview.governance.persistence.entity.RuleGovernanceTraceSpanEntity;
import com.example.disclosurereview.governance.persistence.repository.*;
import com.example.disclosurereview.persistence.entity.ModelCallRecordEntity;
import com.example.disclosurereview.governance.service.GovernanceTraceQueryService;
import com.example.disclosurereview.persistence.repository.LlmCallAttemptJpaRepository;
import com.example.disclosurereview.persistence.repository.ModelCallRecordJpaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class GovernanceTraceQueryServiceTest {
    @Test
    void historicalNoOpRunExplainsWhyModelWasNotCalled() {
        RuleGovernanceRunJpaRepository runs = mock(RuleGovernanceRunJpaRepository.class);
        RuleGovernanceTraceSpanJpaRepository spans = mock(RuleGovernanceTraceSpanJpaRepository.class);
        RuleGovernanceEventJpaRepository events = mock(RuleGovernanceEventJpaRepository.class);
        LlmCallAttemptJpaRepository attempts = mock(LlmCallAttemptJpaRepository.class);
        RuleGovernanceToolCallJpaRepository tools = mock(RuleGovernanceToolCallJpaRepository.class);
        ModelCallRecordJpaRepository modelCalls = mock(ModelCallRecordJpaRepository.class);
        RuleGovernanceRunEntity run = mock(RuleGovernanceRunEntity.class);
        when(run.getId()).thenReturn(3L);
        when(run.getRunNo()).thenReturn("RGR-NOOP-3");
        when(run.getTraceId()).thenReturn("governance-run-3");
        when(run.getStatus()).thenReturn(GovernanceRunStatus.SUCCESS);
        when(run.getStartedAt()).thenReturn(Instant.parse("2026-07-29T01:00:00Z"));
        when(run.getFinishedAt()).thenReturn(Instant.parse("2026-07-29T01:00:00Z"));
        when(runs.findById(3L)).thenReturn(Optional.of(run));
        when(spans.findByGovernanceRunIdOrderByStartedAtAscIdAsc(3L)).thenReturn(List.of());
        when(events.findByGovernanceRun_IdOrderByCreatedAtAsc(3L)).thenReturn(List.of());
        when(attempts.findByGovernanceRunIdOrderById(3L)).thenReturn(List.of());
        when(tools.findByGovernanceRun_IdOrderById(3L)).thenReturn(List.of());
        when(modelCalls.findByGovernanceRunIdOrderById(3L)).thenReturn(List.of());

        var result = new GovernanceTraceQueryService(runs, spans, events, attempts, tools, modelCalls, new ObjectMapper()).trace(3L);

        assertThat(result.currentStep()).isEqualTo("NO_ELIGIBLE_FEEDBACK");
        assertThat(result.currentMessage()).contains("FAILED/DEFERRED", "不会被顶部聚合重复扫描");
        assertThat(result.instrumented()).isFalse();
        assertThat(result.nodes()).extracting(node -> node.type()).containsExactly("RUN", "OUTCOME");
    }

    @Test
    void llmSpanIncludesCollapsedMessageAndToolCallResult() throws Exception {
        RuleGovernanceRunJpaRepository runs = mock(RuleGovernanceRunJpaRepository.class);
        RuleGovernanceTraceSpanJpaRepository spans = mock(RuleGovernanceTraceSpanJpaRepository.class);
        RuleGovernanceEventJpaRepository events = mock(RuleGovernanceEventJpaRepository.class);
        LlmCallAttemptJpaRepository attempts = mock(LlmCallAttemptJpaRepository.class);
        RuleGovernanceToolCallJpaRepository tools = mock(RuleGovernanceToolCallJpaRepository.class);
        ModelCallRecordJpaRepository modelCalls = mock(ModelCallRecordJpaRepository.class);
        RuleGovernanceRunEntity run = mock(RuleGovernanceRunEntity.class);
        when(run.getId()).thenReturn(4L); when(run.getRunNo()).thenReturn("RGR-4");
        when(run.getTraceId()).thenReturn("trace-4"); when(run.getStatus()).thenReturn(GovernanceRunStatus.SUCCESS);
        when(run.getCreatedGroupCount()).thenReturn(1); when(runs.findById(4L)).thenReturn(Optional.of(run));

        Instant start = Instant.parse("2026-07-30T01:00:00Z");
        RuleGovernanceTraceSpanEntity span = new RuleGovernanceTraceSpanEntity();
        span.setSpanId("llm-1"); span.setTraceId("trace-4"); span.setGovernanceRunId(4L); span.setGovernanceGroupId(8L);
        span.setSpanType("LLM_CALL"); span.setSpanName("第 1 轮模型决策"); span.setSpanStatus("SUCCESS");
        span.setExecutionMode("SERIAL"); span.setIterationNumber(1); span.setInputTokenCount(100); span.setOutputTokenCount(20);
        span.setCacheHitTokenCount(0); span.setAttributesJson("{}"); span.setStartedAt(start); span.setFinishedAt(start.plusSeconds(2));
        ModelCallRecordEntity call = new ModelCallRecordEntity();
        call.setGovernanceRunId(4L); call.setGovernanceGroupId(8L); call.setChunkIndex(1); call.setCreatedAt(start.plusSeconds(1));
        call.setProvider("deepseek"); call.setModelName("deepseek-v4-flash");
        String message = "{\"thoughtSummary\":\"准备校验\",\"nextAction\":\"CALL_TOOLS\",\"toolCalls\":[{\"callId\":\"c1\",\"toolName\":\"validateRuleConfig\",\"arguments\":{}}]}";
        call.setStructuredResponse(new ObjectMapper().writeValueAsString(message));
        call.setRawResponse("{\"choices\":[{\"finish_reason\":\"stop\"}]}");
        when(spans.findByGovernanceRunIdOrderByStartedAtAscIdAsc(4L)).thenReturn(List.of(span));
        when(modelCalls.findByGovernanceRunIdOrderById(4L)).thenReturn(List.of(call));

        var result = new GovernanceTraceQueryService(runs, spans, events, attempts, tools, modelCalls, new ObjectMapper()).trace(4L);
        var response = result.nodes().get(0).attributes().path("modelResponse");
        assertThat(response.path("thoughtSummary").asText()).isEqualTo("准备校验");
        assertThat(response.path("toolCalls").path(0).path("toolName").asText()).isEqualTo("validateRuleConfig");
        assertThat(response.path("finishReason").asText()).isEqualTo("stop");
    }

    @Test
    void repeatedExecutionsOfSameGroupKeepOnlyLatestChainExpanded() {
        RuleGovernanceRunJpaRepository runs = mock(RuleGovernanceRunJpaRepository.class);
        RuleGovernanceTraceSpanJpaRepository spans = mock(RuleGovernanceTraceSpanJpaRepository.class);
        RuleGovernanceEventJpaRepository events = mock(RuleGovernanceEventJpaRepository.class);
        LlmCallAttemptJpaRepository attempts = mock(LlmCallAttemptJpaRepository.class);
        RuleGovernanceToolCallJpaRepository tools = mock(RuleGovernanceToolCallJpaRepository.class);
        ModelCallRecordJpaRepository modelCalls = mock(ModelCallRecordJpaRepository.class);
        RuleGovernanceRunEntity run = mock(RuleGovernanceRunEntity.class);
        when(run.getId()).thenReturn(8L); when(run.getRunNo()).thenReturn("RGR-8");
        when(run.getTraceId()).thenReturn("trace-8"); when(run.getStatus()).thenReturn(GovernanceRunStatus.SUCCESS);
        when(run.getCreatedGroupCount()).thenReturn(1); when(runs.findById(8L)).thenReturn(Optional.of(run));

        Instant first = Instant.parse("2026-07-30T01:00:00Z");
        RuleGovernanceTraceSpanEntity root = span("root", null, null, "RUN", first);
        RuleGovernanceTraceSpanEntity firstMessage = span("message-1", "root", 5L, "MESSAGE_CONSUMER", first.plusSeconds(1));
        RuleGovernanceTraceSpanEntity firstAgent = span("agent-1", "message-1", 5L, "AGENT", first.plusSeconds(2));
        RuleGovernanceTraceSpanEntity secondMessage = span("message-2", "root", 5L, "MESSAGE_CONSUMER", first.plusSeconds(20));
        RuleGovernanceTraceSpanEntity secondAgent = span("agent-2", "message-2", 5L, "AGENT", first.plusSeconds(21));
        when(spans.findByGovernanceRunIdOrderByStartedAtAscIdAsc(8L))
                .thenReturn(List.of(root, firstMessage, firstAgent, secondMessage, secondAgent));
        when(modelCalls.findByGovernanceRunIdOrderById(8L)).thenReturn(List.of());

        var result = new GovernanceTraceQueryService(runs, spans, events, attempts, tools, modelCalls, new ObjectMapper()).trace(8L);

        var group = result.nodes().stream().filter(node -> "RETRY_GROUP".equals(node.type())).findFirst().orElseThrow();
        var history = result.nodes().stream().filter(node -> "RETRY_HISTORY".equals(node.type())).findFirst().orElseThrow();
        var latest = result.nodes().stream().filter(node -> "message-2".equals(node.id())).findFirst().orElseThrow();
        var previous = result.nodes().stream().filter(node -> "message-1".equals(node.id())).findFirst().orElseThrow();

        assertThat(result.nodes().stream().filter(node -> "root".equals(node.parentId())))
                .extracting(node -> node.type()).containsExactly("RETRY_GROUP");
        assertThat(group.attributes().path("executionCount").asInt()).isEqualTo(2);
        assertThat(latest.parentId()).isEqualTo(group.id());
        assertThat(latest.attributes().path("isLatestExecution").asBoolean()).isTrue();
        assertThat(history.parentId()).isEqualTo(group.id());
        assertThat(history.attributes().path("collapsedByDefault").asBoolean()).isTrue();
        assertThat(previous.parentId()).isEqualTo(history.id());
        assertThat(firstAgent.getParentSpanId()).isEqualTo("message-1");
        assertThat(secondAgent.getParentSpanId()).isEqualTo("message-2");
    }

    private RuleGovernanceTraceSpanEntity span(String id, String parentId, Long groupId, String type, Instant startedAt) {
        RuleGovernanceTraceSpanEntity span = new RuleGovernanceTraceSpanEntity();
        span.setSpanId(id); span.setParentSpanId(parentId); span.setTraceId("trace-8");
        span.setGovernanceRunId(8L); span.setGovernanceGroupId(groupId); span.setSpanType(type);
        span.setSpanName(type); span.setSpanStatus("SUCCESS"); span.setExecutionMode("SERIAL");
        span.setInputTokenCount(0); span.setOutputTokenCount(0); span.setCacheHitTokenCount(0);
        span.setStartedAt(startedAt); span.setFinishedAt(startedAt.plusSeconds(1));
        return span;
    }
}
