package com.example.disclosurereview.governance.agent;

import com.example.disclosurereview.config.FeedbackGovernanceProperties;
import com.example.disclosurereview.governance.service.RuleProposalService;
import com.example.disclosurereview.governance.service.GovernanceTraceService;
import com.example.disclosurereview.governance.tool.*;
import com.example.disclosurereview.llm.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphDefinition;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncEdgeAction;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.state.AgentState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class GovernanceAgentOrchestrator {
    private final GovernanceAgentPromptBuilder promptBuilder;
    private final GovernanceAgentResponseParser responseParser;
    private final GovernanceAgentToolRegistry toolRegistry;
    private final GovernanceAgentStateService stateService;
    private final RuleProposalService proposalService;
    private final LlmGateway llmGateway;
    private final FeedbackGovernanceProperties properties;
    private final ObjectMapper mapper;
    private final GovernanceTraceService traceService;
    private final GovernanceAgentToolBatchExecutor toolBatchExecutor;

    @Autowired
    public GovernanceAgentOrchestrator(GovernanceAgentPromptBuilder promptBuilder,
                                       GovernanceAgentResponseParser responseParser,
                                       GovernanceAgentToolRegistry toolRegistry,
                                       GovernanceAgentStateService stateService,
                                       RuleProposalService proposalService,
                                       LlmGateway llmGateway,
                                       FeedbackGovernanceProperties properties,
                                       ObjectMapper mapper,
                                       GovernanceTraceService traceService,
                                       GovernanceAgentToolBatchExecutor toolBatchExecutor) {
        this.promptBuilder = promptBuilder;
        this.responseParser = responseParser;
        this.toolRegistry = toolRegistry;
        this.stateService = stateService;
        this.proposalService = proposalService;
        this.llmGateway = llmGateway;
        this.properties = properties;
        this.mapper = mapper;
        this.traceService = traceService;
        this.toolBatchExecutor = toolBatchExecutor;
    }

    public GovernanceAgentOrchestrator(GovernanceAgentPromptBuilder promptBuilder,
                                       GovernanceAgentResponseParser responseParser,
                                       GovernanceAgentToolRegistry toolRegistry,
                                       GovernanceAgentStateService stateService,
                                       RuleProposalService proposalService,
                                       LlmGateway llmGateway,
                                       FeedbackGovernanceProperties properties,
                                       ObjectMapper mapper) {
        this(promptBuilder, responseParser, toolRegistry, stateService, proposalService,
                llmGateway, properties, mapper, null,
                new GovernanceAgentToolBatchExecutor(toolRegistry, Runnable::run));
    }

    public AnalysisResult analyze(Long runId, Long groupId) {
        GovernanceTraceService.SpanScope agentSpan = traceService == null ? GovernanceTraceService.SpanScope.noop()
                : traceService.open(runId, groupId, "AGENT", "反馈治理 Agent", "SERIAL", null,
                1, null, null, null, java.util.Map.of(
                        "toolCallingMode", properties.getAgent().getToolCallingMode(),
                        "maximumModelIterations", properties.getAgent().getMaxModelIterations(),
                        "maximumToolsPerRound", properties.getAgent().getMaxToolsPerRound(),
                        "maximumTotalToolCalls", properties.getAgent().getMaxTotalToolCalls()));
        stateService.begin(runId, groupId);
        Instant deadline = Instant.now().plus(properties.getAgent().timeout());
        try {
            String mode = properties.getAgent().getToolCallingMode();
            AnalysisResult result;
            if ("NATIVE".equalsIgnoreCase(mode) || "AUTO".equalsIgnoreCase(mode)) {
                try {
                    result = nativeLoop(runId, groupId, deadline);
                } catch (GovernanceNoProposalException terminal) {
                    throw terminal;
                } catch (RuntimeException nativeFailure) {
                    if (Instant.now().isAfter(deadline)) throw nativeFailure;
                    result = structuredLoop(runId, groupId, deadline,
                            List.of(new GovernanceAgentPromptBuilder.HistoryEntry("system", null,
                                    "原生 Tool Calling 失败，已切换结构化模式：" + safe(nativeFailure))));
                }
            } else {
                result = structuredLoop(runId, groupId, deadline, List.of());
            }
            agentSpan.success();
            return result;
        } catch (GovernanceNoProposalException terminal) {
            stateService.defer(groupId, safe(terminal));
            agentSpan.finish("DEFERRED", LlmUsage.empty(), safe(terminal));
            return new AnalysisResult(null, null, null, 0);
        } catch (RuntimeException e) {
            stateService.fail(groupId, safe(e));
            agentSpan.fail(e);
            throw e;
        } finally {
            agentSpan.close();
        }
    }

    private AnalysisResult structuredLoop(Long runId,
                                          Long groupId,
                                          Instant deadline,
                                          List<GovernanceAgentPromptBuilder.HistoryEntry> initialHistory) {
        try {
            StateGraph<StructuredGraphState> workflow = new StateGraph<>(StructuredGraphState::new);
            workflow.addNode("ANALYSIS_BRIEF", AsyncNodeAction.node_async(this::analysisBriefNode));
            workflow.addNode("MODEL_DECISION", AsyncNodeAction.node_async(this::modelDecisionNode));
            workflow.addNode("TOOL_BATCH", AsyncNodeAction.node_async(this::toolBatchNode));
            workflow.addEdge(GraphDefinition.START, "ANALYSIS_BRIEF");
            workflow.addEdge("ANALYSIS_BRIEF", "MODEL_DECISION");
            workflow.addConditionalEdges("MODEL_DECISION", AsyncEdgeAction.edge_async(this::route), Map.of(
                    "MODEL", "MODEL_DECISION", "TOOLS", "TOOL_BATCH", "END", GraphDefinition.END));
            workflow.addConditionalEdges("TOOL_BATCH", AsyncEdgeAction.edge_async(this::route), Map.of(
                    "MODEL", "MODEL_DECISION", "END", GraphDefinition.END));
            CompiledGraph<StructuredGraphState> graph = workflow.compile();
            graph.setMaxIterations(properties.getAgent().getMaxModelIterations() * 3 + 8);
            StructuredGraphState finalState = graph.invoke(graphInitialState(runId, groupId, deadline, initialHistory))
                    .orElseThrow(() -> new GovernanceNoProposalException("LangGraph 未返回最终状态"));
            Long proposalId = finalState.proposalId();
            if (proposalId == null) {
                throw new GovernanceNoProposalException("LangGraph 已完成 " + finalState.modelRound() + "/"
                        + properties.getAgent().getMaxModelIterations() + " 轮模型对话、执行 "
                        + finalState.totalToolCalls() + " 个 Tool，但未形成可安全提交的提案"
                        + (finalState.lastError() == null ? "" : "；最后原因：" + finalState.lastError()));
            }
            return new AnalysisResult(proposalId, finalState.provider(), finalState.model(), finalState.totalToolCalls());
        } catch (GovernanceNoProposalException e) {
            throw e;
        } catch (Exception e) {
            Throwable cause = e;
            while (cause.getCause() != null && cause != cause.getCause()) cause = cause.getCause();
            if (cause instanceof GovernanceNoProposalException noProposal) throw noProposal;
            if (cause instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("LangGraph 治理流程执行失败: " + safe(e), e);
        }
    }

    private Map<String, Object> graphInitialState(Long runId, Long groupId, Instant deadline,
                                                   List<GovernanceAgentPromptBuilder.HistoryEntry> initialHistory) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("runId", runId); state.put("groupId", groupId); state.put("deadline", deadline);
        state.put("history", new ArrayList<>(initialHistory)); state.put("modelRound", 0);
        state.put("totalToolCalls", 0); state.put("route", "MODEL");
        return state;
    }

    private Map<String, Object> analysisBriefNode(StructuredGraphState state) {
        return tracedGraphNode(state, "ANALYSIS_BRIEF", 1, () -> {
        requireTime(state.deadline());
        var request = new GovernanceAgentToolBatchExecutor.ToolRequest(
                "analysis-brief", "getGovernanceAnalysisBrief",
                mapper.createObjectNode().put("groupId", state.groupId()), 1);
        GovernanceAgentToolBatchExecutor.BatchResult batch = toolBatchExecutor.execute(
                state.runId(), state.groupId(), 0, List.of(request));
        GovernanceAgentToolBatchExecutor.ToolOutcome outcome = batch.outcomes().get(0);
        if (!outcome.success() || outcome.result() == null) {
            throw new GovernanceNoProposalException("LangGraph ANALYSIS_BRIEF 节点失败: " + outcome.error());
        }
        List<GovernanceAgentPromptBuilder.HistoryEntry> history = copyHistory(state);
        history.add(new GovernanceAgentPromptBuilder.HistoryEntry(
                "analysis_brief", "getGovernanceAnalysisBrief", compactAnalysisBrief(outcome.result().output())));
        return updates("history", history, "totalToolCalls", state.totalToolCalls() + 1,
                "route", "MODEL", "lastError", null);
        });
    }

    private Map<String, Object> modelDecisionNode(StructuredGraphState state) {
        return tracedGraphNode(state, "MODEL_DECISION", 2, () -> {
        if (Instant.now().isAfter(state.deadline())) {
            return updates("route", "END", "lastError", timeoutMessage(state, null));
        }
        int iteration = state.modelRound() + 1;
        if (iteration > properties.getAgent().getMaxModelIterations()) {
            return updates("route", "END", "lastError", "已达到模型对话轮数上限");
        }
        List<GovernanceAgentPromptBuilder.HistoryEntry> history = copyHistory(state);
        LlmCallContext callContext = LlmCallContext.governance(state.runId(), state.groupId(), null,
                "FEEDBACK_GOVERNANCE_AGENT", properties.getAgent().getPromptVersion())
                .withChunk(iteration, null, null);
        String userPrompt = promptBuilder.structuredUserPrompt(state.runId(), state.groupId(), history)
                + "\n当前硬限制：单轮最多调用 " + properties.getAgent().getMaxToolsPerRound()
                + " 个 Tool，剩余总 Tool 预算 "
                + Math.max(0, properties.getAgent().getMaxTotalToolCalls() - state.totalToolCalls()) + "。";
        LlmGatewayResponse<String> response = llmGateway.chatCompletion(callContext,
                promptBuilder.systemPrompt(), userPrompt, value -> value);
        GovernanceAgentResponseParser.AgentStep step;
        try {
            step = responseParser.parse(response.result());
        } catch (RuntimeException parseFailure) {
            history.add(new GovernanceAgentPromptBuilder.HistoryEntry("assistant", null, response.result()));
            history.add(new GovernanceAgentPromptBuilder.HistoryEntry("tool_error", null, safe(parseFailure)));
            return updates("history", history, "modelRound", iteration, "route", "MODEL",
                    "lastError", safe(parseFailure));
        }
        history.add(new GovernanceAgentPromptBuilder.HistoryEntry("assistant", step.toolName(), step.raw()));
        String validationError = validateStep(step, state.totalToolCalls());
        if (validationError != null) {
            history.add(new GovernanceAgentPromptBuilder.HistoryEntry("tool_error", null, validationError));
            return updates("history", history, "modelRound", iteration, "route", "MODEL",
                    "lastError", validationError);
        }
        return updates("history", history, "modelRound", iteration, "route", "TOOLS",
                "stepRaw", step.raw().toString(),
                "modelCallId", response.modelCallRecord() == null ? null : response.modelCallRecord().getId(),
                "provider", response.providerCode(), "model", response.modelName(), "lastError", null);
        });
    }

    private Map<String, Object> toolBatchNode(StructuredGraphState state) {
        return tracedGraphNode(state, "TOOL_BATCH", 3, () -> {
        GovernanceAgentResponseParser.AgentStep step = responseParser.parse(state.stepRaw());
        boolean finalProposal = isProposalBatch(step);
        if (Instant.now().isAfter(state.deadline()) && !finalProposal) {
            return updates("route", "END", "lastError", timeoutMessage(state,
                    "模型返回的非提案 Tool 批次未执行: " + step.toolCalls().stream()
                            .map(GovernanceAgentResponseParser.AgentToolCall::toolName).toList()));
        }
        int remainingTotal = properties.getAgent().getMaxTotalToolCalls() - state.totalToolCalls();
        int allowed = Math.min(Math.min(properties.getAgent().getMaxToolsPerRound(), remainingTotal),
                step.toolCalls().size());
        if (allowed <= 0) {
            return updates("route", "END", "lastError", "总 Tool 调用预算已耗尽，未形成提案");
        }
        List<GovernanceAgentResponseParser.AgentToolCall> executable = step.toolCalls().subList(0, allowed);
        List<GovernanceAgentResponseParser.AgentToolCall> deferred = step.toolCalls().subList(allowed, step.toolCalls().size());
        GovernanceAgentToolBatchExecutor.BatchResult batch = toolBatchExecutor.execute(
                state.runId(), state.groupId(), state.modelRound(), requests(executable));
        int executed = (int) batch.outcomes().stream().filter(outcome -> !outcome.skipped()).count();
        List<GovernanceAgentPromptBuilder.HistoryEntry> history = copyHistory(state);
        history.add(new GovernanceAgentPromptBuilder.HistoryEntry("tool_batch", null, batchHistory(batch, deferred)));
        Map<String, Object> updates = updates("history", history,
                "totalToolCalls", state.totalToolCalls() + executed,
                "route", batch.proposalId() == null ? "MODEL" : "END");
        if (batch.proposalId() != null) {
            proposalService.attachAgentCallById(batch.proposalId(), state.modelCallId(),
                    state.provider(), state.model(), json(step.raw()));
            updates.put("proposalId", batch.proposalId());
        } else {
            batch.outcomes().stream().filter(outcome -> !outcome.success()).map(GovernanceAgentToolBatchExecutor.ToolOutcome::error)
                    .filter(java.util.Objects::nonNull).findFirst().ifPresent(error -> updates.put("lastError", error));
            if (!deferred.isEmpty()) {
                updates.put("lastError", "本轮已执行前 " + allowed + " 个 Tool；以下请求因单轮上限延期，"
                        + "下一轮只调用这些 Tool: " + deferred.stream()
                        .map(GovernanceAgentResponseParser.AgentToolCall::toolName).toList());
            }
        }
        return updates;
        });
    }

    private String validateStep(GovernanceAgentResponseParser.AgentStep step, int totalToolCalls) {
        if ("FINISH".equals(step.nextAction())) return "尚未通过 propose* Tool 创建提案，不能 FINISH";
        List<String> unknown = step.toolCalls().stream().map(GovernanceAgentResponseParser.AgentToolCall::toolName)
                .filter(name -> !toolRegistry.registered(name)).toList();
        if (!unknown.isEmpty()) return "不存在的 Tool: " + String.join(", ", unknown) + "；只能使用 Tool 使用摘要中的名称";
        long proposalCalls = step.toolCalls().stream().filter(call -> call.toolName().startsWith("propose")).count();
        if (proposalCalls > 0 && (proposalCalls != 1 || step.toolCalls().size() != 1)) {
            return "propose* 必须独占一轮，不能与其他 Tool 混合";
        }
        if (totalToolCalls >= properties.getAgent().getMaxTotalToolCalls()) {
            return "总 Tool 调用预算已耗尽；请直接形成安全提案或 NO_ACTION";
        }
        return null;
    }

    private boolean isProposalBatch(GovernanceAgentResponseParser.AgentStep step) {
        return step.toolCalls().size() == 1 && step.toolCalls().get(0).toolName().startsWith("propose");
    }

    private String route(StructuredGraphState state) { return state.route(); }
    private Map<String, Object> tracedGraphNode(StructuredGraphState state, String nodeName, int sequence,
                                                java.util.function.Supplier<Map<String, Object>> action) {
        GovernanceTraceService.SpanScope span = traceService == null ? GovernanceTraceService.SpanScope.noop()
                : traceService.open(state.runId(), state.groupId(), "GRAPH_NODE", nodeName,
                "SERIAL", null, state.modelRound() * 10 + sequence, state.modelRound(), null, null,
                Map.of("graph", "LangGraph4j", "node", nodeName, "modelRound", state.modelRound()));
        try {
            Map<String, Object> result = action.get();
            span.success();
            return result;
        } catch (RuntimeException e) {
            span.fail(e);
            throw e;
        } finally {
            span.close();
        }
    }
    private List<GovernanceAgentPromptBuilder.HistoryEntry> copyHistory(StructuredGraphState state) {
        return new ArrayList<>(state.history());
    }
    private com.fasterxml.jackson.databind.JsonNode compactAnalysisBrief(com.fasterxml.jackson.databind.JsonNode source) {
        var compact = mapper.createObjectNode();
        for (String key : List.of("group", "ruleDefinition", "sourceRuleVersion", "feedbackSamples",
                "executionRecords", "historicalDecisions", "similarAcceptedProposals",
                "similarRejectedProposals", "availableExecutorSchemas")) {
            if (source.has(key)) compact.set(key, source.path(key));
        }
        return compact;
    }
    private Map<String, Object> updates(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index + 1 < values.length; index += 2) result.put(String.valueOf(values[index]), values[index + 1]);
        return result;
    }

    private static final class StructuredGraphState extends AgentState {
        private StructuredGraphState(Map<String, Object> data) { super(data); }
        private Long runId() { return ((Number) value("runId").orElseThrow()).longValue(); }
        private Long groupId() { return ((Number) value("groupId").orElseThrow()).longValue(); }
        private Instant deadline() { return value("deadline").map(Instant.class::cast).orElseThrow(); }
        @SuppressWarnings("unchecked")
        private List<GovernanceAgentPromptBuilder.HistoryEntry> history() { return (List<GovernanceAgentPromptBuilder.HistoryEntry>) value("history").orElse(List.of()); }
        private int modelRound() { return ((Number) value("modelRound").orElse(0)).intValue(); }
        private int totalToolCalls() { return ((Number) value("totalToolCalls").orElse(0)).intValue(); }
        private String route() { return String.valueOf(value("route").orElse("END")); }
        private String lastError() { return value("lastError").map(String::valueOf).orElse(null); }
        private Long proposalId() { return value("proposalId").map(value -> ((Number) value).longValue()).orElse(null); }
        private String provider() { return value("provider").map(String::valueOf).orElse(null); }
        private String model() { return value("model").map(String::valueOf).orElse(null); }
        private String stepRaw() { return value("stepRaw").map(String::valueOf).orElseThrow(); }
        private Long modelCallId() { return value("modelCallId").map(value -> ((Number) value).longValue()).orElse(null); }
    }

    private AnalysisResult nativeLoop(Long runId, Long groupId, Instant deadline) {
        var briefRequest = new GovernanceAgentToolBatchExecutor.ToolRequest(
                "analysis-brief", "getGovernanceAnalysisBrief",
                mapper.createObjectNode().put("groupId", groupId), 1);
        GovernanceAgentToolBatchExecutor.BatchResult briefBatch = toolBatchExecutor.execute(
                runId, groupId, 0, List.of(briefRequest));
        GovernanceAgentToolBatchExecutor.ToolOutcome briefOutcome = briefBatch.outcomes().get(0);
        if (!briefOutcome.success() || briefOutcome.result() == null) {
            throw new GovernanceNoProposalException("LangGraph ANALYSIS_BRIEF 节点失败: " + briefOutcome.error());
        }
        List<LlmAgentMessage> messages = new ArrayList<>();
        messages.add(LlmAgentMessage.user(promptBuilder.initialNativePrompt(runId, groupId)
                + "\nanalysis_brief=" + json(compactAnalysisBrief(briefOutcome.result().output()))));
        int maximumRounds = properties.getAgent().getMaxModelIterations();
        int totalToolCalls = 1;
        for (int modelIteration = 1; modelIteration <= maximumRounds
                && totalToolCalls < properties.getAgent().getMaxTotalToolCalls(); modelIteration++) {
            requireTime(deadline);
            LlmCallContext context = LlmCallContext.governance(runId, groupId, null,
                    "FEEDBACK_GOVERNANCE_AGENT", properties.getAgent().getPromptVersion())
                    .withChunk(modelIteration, null, null);
            LlmGatewayResponse<LlmAgentProviderResponse> response = llmGateway.agentCompletion(
                    context, promptBuilder.systemPrompt(), messages, promptBuilder.nativeTools());
            LlmAgentProviderResponse agent = response.result();
            messages.add(LlmAgentMessage.assistant(agent.content(), agent.toolCalls()));
            if (agent.toolCalls().isEmpty()) {
                messages.add(LlmAgentMessage.user("你尚未创建提案。必须继续调用已注册 Tool，最终调用一个 propose* Tool。"));
                continue;
            }
            int remaining = properties.getAgent().getMaxTotalToolCalls() - totalToolCalls;
            int allowed = Math.min(Math.min(agent.toolCalls().size(), properties.getAgent().getMaxToolsPerRound()), remaining);
            List<LlmToolCall> executable = agent.toolCalls().subList(0, allowed);
            List<GovernanceAgentToolBatchExecutor.ToolRequest> requests = new ArrayList<>();
            for (int index = 0; index < executable.size(); index++) {
                LlmToolCall call = executable.get(index);
                requests.add(new GovernanceAgentToolBatchExecutor.ToolRequest(call.id(), call.name(), call.arguments(), index + 1));
            }
            GovernanceAgentToolBatchExecutor.BatchResult batch = toolBatchExecutor.execute(runId, groupId, modelIteration, requests);
            totalToolCalls += (int) batch.outcomes().stream().filter(outcome -> !"SKIPPED".equals(outcome.status())).count();
            for (GovernanceAgentToolBatchExecutor.ToolOutcome outcome : batch.outcomes()) {
                messages.add(LlmAgentMessage.tool(outcome.callId(), json(toolOutcome(outcome))));
            }
            for (int index = allowed; index < agent.toolCalls().size(); index++) {
                LlmToolCall rejected = agent.toolCalls().get(index);
                messages.add(LlmAgentMessage.tool(rejected.id(), json(java.util.Map.of(
                        "success", false,
                        "error", "本轮或总 Tool 调用上限已达到，请在下一轮仅请求必要 Tool"))));
            }
            if (batch.proposalId() != null) {
                proposalService.attachAgentCall(batch.proposalId(), response.modelCallRecord(),
                        response.providerCode(), response.modelName(), agent.rawResponse());
                return new AnalysisResult(batch.proposalId(), response.providerCode(), response.modelName(), totalToolCalls);
            }
        }
        throw new GovernanceNoProposalException("Agent 已完成最大模型对话轮数或总 Tool 调用预算，但未形成可安全提交的提案");
    }

    private List<GovernanceAgentToolBatchExecutor.ToolRequest> requests(
            List<GovernanceAgentResponseParser.AgentToolCall> calls) {
        List<GovernanceAgentToolBatchExecutor.ToolRequest> requests = new ArrayList<>();
        for (int index = 0; index < calls.size(); index++) {
            GovernanceAgentResponseParser.AgentToolCall call = calls.get(index);
            requests.add(new GovernanceAgentToolBatchExecutor.ToolRequest(
                    call.callId(), call.toolName(), call.arguments(), index + 1));
        }
        return requests;
    }

    private com.fasterxml.jackson.databind.JsonNode batchHistory(
            GovernanceAgentToolBatchExecutor.BatchResult batch,
            List<GovernanceAgentResponseParser.AgentToolCall> deferred) {
        var root = mapper.createObjectNode();
        root.put("executionMode", batch.executionMode());
        var results = root.putArray("results");
        batch.outcomes().forEach(outcome -> results.add(toolOutcome(outcome)));
        if (deferred != null && !deferred.isEmpty()) {
            root.put("batchLimited", true);
            root.put("maxToolsPerRound", properties.getAgent().getMaxToolsPerRound());
            var pending = root.putArray("deferredToolCalls");
            deferred.forEach(call -> pending.addObject().put("callId", call.callId()).put("toolName", call.toolName()));
            root.put("nextStep", "下一轮只调用 deferredToolCalls，保持原参数不变");
        }
        return root;
    }

    private com.fasterxml.jackson.databind.JsonNode toolOutcome(GovernanceAgentToolBatchExecutor.ToolOutcome outcome) {
        var node = mapper.createObjectNode();
        node.put("callId", outcome.callId()); node.put("toolName", outcome.toolName());
        node.put("status", outcome.status()); node.put("executionMode", outcome.executionMode());
        if (outcome.result() != null) {
            node.set("output", outcome.result().output());
            if (outcome.result().candidateHash() != null) node.put("candidateHash", outcome.result().candidateHash());
            if (outcome.result().proposalId() != null) node.put("proposalId", outcome.result().proposalId());
        }
        if (outcome.error() != null) node.put("error", outcome.error());
        return node;
    }

    private void requireTime(Instant deadline) {
        if (Instant.now().isAfter(deadline)) throw new GovernanceNoProposalException("Agent 执行超时，未形成可安全提交的提案");
    }
    private String timeoutMessage(StructuredGraphState state, String detail) {
        StringBuilder message = new StringBuilder("Agent 执行达到时间上限，已正常结束状态机");
        if (detail != null && !detail.isBlank()) message.append("；").append(detail);
        if (state.lastError() != null && !state.lastError().isBlank()) {
            message.append("；上一原因：").append(state.lastError());
        }
        return message.toString();
    }
    private String json(Object value) { try { return mapper.writeValueAsString(value); } catch (Exception e) { return "{}"; } }
    private String safe(Throwable error) { String value = error.getMessage(); if (value == null) value = error.getClass().getSimpleName(); return value.length() > 2000 ? value.substring(0, 2000) : value; }

    public record AnalysisResult(Long proposalId, String provider, String model, int toolIterations) {}
}
