package com.example.disclosurereview.governance.agent;

import com.example.disclosurereview.governance.service.GovernanceTraceService;
import com.example.disclosurereview.governance.tool.*;
import com.example.disclosurereview.llm.LlmUsage;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Component
public class GovernanceAgentToolBatchExecutor {
    private final GovernanceAgentToolRegistry registry;
    private final Executor executor;
    private final GovernanceTraceService traceService;

    @Autowired
    public GovernanceAgentToolBatchExecutor(GovernanceAgentToolRegistry registry,
                                            @Qualifier("governanceToolExecutor") Executor executor,
                                            GovernanceTraceService traceService) {
        this.registry = registry;
        this.executor = executor;
        this.traceService = traceService;
    }

    public GovernanceAgentToolBatchExecutor(GovernanceAgentToolRegistry registry, Executor executor) {
        this.registry = registry;
        this.executor = executor;
        this.traceService = null;
    }

    public BatchResult execute(Long runId, Long groupId, int modelRound, List<ToolRequest> requests) {
        if (requests == null || requests.isEmpty()) return new BatchResult(List.of(), "SERIAL", null);
        boolean parallel = requests.size() > 1 && requests.stream().allMatch(request -> registry.parallelSafe(request.toolName()));
        String executionMode = parallel ? "PARALLEL" : "SERIAL";
        String parallelGroup = parallel
                ? "tool-batch-" + runId + "-" + groupId + "-" + modelRound + "-" + UUID.randomUUID()
                : null;
        GovernanceTraceService.SpanScope batchSpan = traceService == null ? GovernanceTraceService.SpanScope.noop()
                : traceService.open(runId, groupId, "TOOL_BATCH", "第 " + modelRound + " 轮 Tool 批次",
                "SERIAL", null, modelRound * 100, modelRound, null, null,
                Map.of("toolCount", requests.size(), "childExecutionMode", executionMode));
        try {
            List<ToolOutcome> outcomes = parallel
                    ? executeParallel(runId, groupId, modelRound, requests, parallelGroup, batchSpan.spanId())
                    : executeSerial(runId, groupId, modelRound, requests, batchSpan.spanId());
            long executed = outcomes.stream().filter(outcome -> !outcome.skipped()).count();
            long successes = outcomes.stream().filter(ToolOutcome::success).count();
            String status = successes == executed ? "SUCCESS" : successes == 0 ? "FAILED" : "PARTIAL_SUCCESS";
            batchSpan.finish(status, LlmUsage.empty(), outcomes.stream()
                    .filter(outcome -> !outcome.success() && outcome.error() != null)
                    .map(ToolOutcome::error).findFirst().orElse(null));
            Long proposalId = outcomes.stream().map(ToolOutcome::result).filter(java.util.Objects::nonNull)
                    .map(ToolExecutionResult::proposalId).filter(java.util.Objects::nonNull).findFirst().orElse(null);
            return new BatchResult(outcomes, executionMode, proposalId);
        } catch (RuntimeException e) {
            batchSpan.fail(e);
            throw e;
        } finally {
            batchSpan.close();
        }
    }

    private List<ToolOutcome> executeParallel(Long runId, Long groupId, int round,
                                              List<ToolRequest> requests, String parallelGroup,
                                              String traceParentSpanId) {
        List<CompletableFuture<ToolOutcome>> futures = requests.stream()
                .map(request -> CompletableFuture.supplyAsync(() -> executeOne(runId, groupId, round, request,
                        "PARALLEL", parallelGroup, traceParentSpanId), executor))
                .toList();
        return futures.stream().map(CompletableFuture::join).toList();
    }

    private List<ToolOutcome> executeSerial(Long runId, Long groupId, int round,
                                            List<ToolRequest> requests, String traceParentSpanId) {
        List<ToolOutcome> outcomes = new ArrayList<>();
        for (int index = 0; index < requests.size(); index++) {
            ToolRequest request = requests.get(index);
            ToolOutcome outcome = executeOne(runId, groupId, round, request,
                    "SERIAL", null, traceParentSpanId);
            outcomes.add(outcome);
            if (outcome.result() != null && outcome.result().proposalId() != null) {
                for (int skipped = index + 1; skipped < requests.size(); skipped++) {
                    ToolRequest remaining = requests.get(skipped);
                    outcomes.add(new ToolOutcome(remaining.callId(), remaining.toolName(), remaining.toolIndex(),
                            "SKIPPED", null, "提案 Tool 已完成，后续同批调用未执行",
                            "SERIAL", null));
                }
                break;
            }
        }
        return List.copyOf(outcomes);
    }

    private ToolOutcome executeOne(Long runId, Long groupId, int round, ToolRequest request,
                                   String executionMode, String parallelGroup, String traceParentSpanId) {
        try {
            ToolExecutionResult result = registry.execute(request.toolName(), request.arguments(),
                    new GovernanceToolExecutionContext(runId, groupId, round, "GOVERNANCE_AGENT",
                            request.toolIndex(), executionMode, parallelGroup, traceParentSpanId));
            return new ToolOutcome(request.callId(), request.toolName(), request.toolIndex(),
                    "SUCCESS", result, null, executionMode, parallelGroup);
        } catch (RuntimeException e) {
            return new ToolOutcome(request.callId(), request.toolName(), request.toolIndex(),
                    "FAILED", null, safe(e), executionMode, parallelGroup);
        }
    }

    private String safe(Throwable error) {
        String value = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        return value.length() > 2000 ? value.substring(0, 2000) : value;
    }

    public record ToolRequest(String callId, String toolName, JsonNode arguments, int toolIndex) {}
    public record ToolOutcome(String callId, String toolName, int toolIndex, String status,
                              ToolExecutionResult result, String error,
                              String executionMode, String parallelGroup) {
        public boolean success() { return "SUCCESS".equals(status); }
        public boolean skipped() { return "SKIPPED".equals(status); }
    }
    public record BatchResult(List<ToolOutcome> outcomes, String executionMode, Long proposalId) {}
}
