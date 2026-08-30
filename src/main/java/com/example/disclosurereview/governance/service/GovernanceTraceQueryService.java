package com.example.disclosurereview.governance.service;

import com.example.disclosurereview.governance.dto.RuleGovernanceDtos.*;
import com.example.disclosurereview.governance.persistence.entity.*;
import com.example.disclosurereview.governance.persistence.repository.*;
import com.example.disclosurereview.persistence.entity.LlmCallAttemptEntity;
import com.example.disclosurereview.persistence.entity.ModelCallRecordEntity;
import com.example.disclosurereview.persistence.repository.LlmCallAttemptJpaRepository;
import com.example.disclosurereview.persistence.repository.ModelCallRecordJpaRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.*;

@Service
public class GovernanceTraceQueryService {
    private final RuleGovernanceRunJpaRepository runRepository;
    private final RuleGovernanceTraceSpanJpaRepository spanRepository;
    private final RuleGovernanceEventJpaRepository eventRepository;
    private final LlmCallAttemptJpaRepository attemptRepository;
    private final RuleGovernanceToolCallJpaRepository toolRepository;
    private final ModelCallRecordJpaRepository modelCallRepository;
    private final ObjectMapper mapper;

    public GovernanceTraceQueryService(RuleGovernanceRunJpaRepository runRepository,
                                       RuleGovernanceTraceSpanJpaRepository spanRepository,
                                       RuleGovernanceEventJpaRepository eventRepository,
                                       LlmCallAttemptJpaRepository attemptRepository,
                                       RuleGovernanceToolCallJpaRepository toolRepository,
                                       ModelCallRecordJpaRepository modelCallRepository,
                                       ObjectMapper mapper) {
        this.runRepository = runRepository;
        this.spanRepository = spanRepository;
        this.eventRepository = eventRepository;
        this.attemptRepository = attemptRepository;
        this.toolRepository = toolRepository;
        this.modelCallRepository = modelCallRepository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public TraceResponse trace(Long runId) {
        RuleGovernanceRunEntity run = runRepository.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("治理运行不存在: " + runId));
        List<RuleGovernanceTraceSpanEntity> spans = spanRepository.findByGovernanceRunIdOrderByStartedAtAscIdAsc(runId);
        List<ModelCallRecordEntity> modelCalls = modelCallRepository.findByGovernanceRunIdOrderById(runId);
        List<TraceNodeResponse> nodes;
        boolean instrumented = !spans.isEmpty();
        if (instrumented) {
            List<TraceNodeResponse> rawNodes = spans.stream().map(span -> node(span, modelCalls)).toList();
            nodes = foldRepeatedGroupExecutions(runId, rawNodes);
        }
        else nodes = legacyNodes(run, modelCalls);
        List<TraceEdgeResponse> edges = nodes.stream()
                .filter(node -> StringUtils.hasText(node.parentId()))
                .map(node -> new TraceEdgeResponse(node.parentId(), node.id(), node.executionMode()))
                .toList();
        Current current = current(run, nodes);
        return new TraceResponse(run.getId(), run.getRunNo(), run.getTraceId(), run.getStatus(),
                current.step(), current.message(), instrumented, nodes, edges);
    }

    /**
     * A manual re-analysis or a RabbitMQ redelivery creates another MESSAGE_CONSUMER span for the same
     * governance group. Rendering those spans as root siblings makes one logical group look like several
     * independent call graphs. Keep every attempt for diagnostics, but expose only the latest execution as
     * the primary branch and put earlier executions below one collapsed history node.
     */
    private List<TraceNodeResponse> foldRepeatedGroupExecutions(Long runId, List<TraceNodeResponse> source) {
        Map<String, TraceNodeResponse> byId = new HashMap<>();
        source.forEach(node -> byId.put(node.id(), node));

        Map<Long, List<TraceNodeResponse>> attemptsByGroup = new LinkedHashMap<>();
        for (TraceNodeResponse node : source) {
            TraceNodeResponse parent = byId.get(node.parentId());
            if (node.governanceGroupId() != null
                    && "MESSAGE_CONSUMER".equals(node.type())
                    && parent != null && "RUN".equals(parent.type())) {
                attemptsByGroup.computeIfAbsent(node.governanceGroupId(), ignored -> new ArrayList<>()).add(node);
            }
        }

        Map<String, TraceNodeResponse> replacements = new HashMap<>();
        List<TraceNodeResponse> synthetic = new ArrayList<>();
        Comparator<TraceNodeResponse> chronological = Comparator
                .comparing((TraceNodeResponse node) -> node.startedAt() == null ? Instant.EPOCH : node.startedAt())
                .thenComparing(TraceNodeResponse::id);

        for (Map.Entry<Long, List<TraceNodeResponse>> entry : attemptsByGroup.entrySet()) {
            List<TraceNodeResponse> attempts = entry.getValue().stream().sorted(chronological).toList();
            if (attempts.size() < 2) continue;

            Long groupId = entry.getKey();
            TraceNodeResponse latest = attempts.get(attempts.size() - 1);
            String groupNodeId = "retry-group-" + runId + "-" + groupId;
            String historyNodeId = "retry-history-" + runId + "-" + groupId;
            Instant firstStartedAt = attempts.stream().map(TraceNodeResponse::startedAt)
                    .filter(Objects::nonNull).min(Instant::compareTo).orElse(latest.startedAt());
            Instant latestFinishedAt = latest.finishedAt() == null ? null : attempts.stream()
                    .map(TraceNodeResponse::finishedAt).filter(Objects::nonNull)
                    .max(Instant::compareTo).orElse(latest.finishedAt());
            Instant historyFinishedAt = attempts.subList(0, attempts.size() - 1).stream()
                    .map(TraceNodeResponse::finishedAt).filter(Objects::nonNull)
                    .max(Instant::compareTo).orElse(null);

            ObjectNode groupAttributes = mapper.createObjectNode();
            groupAttributes.put("retryPresentation", true);
            groupAttributes.put("executionCount", attempts.size());
            groupAttributes.put("latestExecutionId", latest.id());
            synthetic.add(new TraceNodeResponse(groupNodeId, latest.parentId(), groupId,
                    "RETRY_GROUP", "治理分组 #" + groupId + " · 共 " + attempts.size() + " 次执行",
                    latest.status(), latest.executionMode(), latest.parallelGroup(), latest.sequence(), null,
                    null, null, 0, 0, 0, elapsed(firstStartedAt, latestFinishedAt), latest.errorMessage(),
                    groupAttributes, firstStartedAt, latestFinishedAt));

            ObjectNode historyAttributes = mapper.createObjectNode();
            historyAttributes.put("collapsedByDefault", true);
            historyAttributes.put("executionCount", attempts.size() - 1);
            historyAttributes.put("latestExecutionExcluded", true);
            synthetic.add(new TraceNodeResponse(historyNodeId, groupNodeId, groupId,
                    "RETRY_HISTORY", "历史执行（" + (attempts.size() - 1) + " 次）",
                    "HISTORICAL", "SERIAL", null, 2, null, null, null,
                    0, 0, 0, null, null, historyAttributes,
                    firstStartedAt, historyFinishedAt));

            for (int index = 0; index < attempts.size(); index++) {
                TraceNodeResponse attempt = attempts.get(index);
                boolean isLatest = index == attempts.size() - 1;
                ObjectNode attributes = attempt.attributes() != null && attempt.attributes().isObject()
                        ? ((ObjectNode) attempt.attributes()).deepCopy() : mapper.createObjectNode();
                attributes.put("executionOrdinal", index + 1);
                attributes.put("isLatestExecution", isLatest);
                replacements.put(attempt.id(), copy(attempt,
                        isLatest ? groupNodeId : historyNodeId,
                        attempt.name() + (isLatest ? " · 当前执行（第 " : " · 历史执行（第 ") + (index + 1) + " 次）",
                        isLatest ? 1 : index + 1, attributes));
            }
        }

        if (synthetic.isEmpty()) return source;
        List<TraceNodeResponse> result = new ArrayList<>(source.size() + synthetic.size());
        for (TraceNodeResponse node : source) result.add(replacements.getOrDefault(node.id(), node));
        result.addAll(synthetic);
        return result.stream().sorted(Comparator
                .comparing((TraceNodeResponse node) -> node.startedAt() == null ? Instant.EPOCH : node.startedAt())
                .thenComparing(TraceNodeResponse::id)).toList();
    }

    private TraceNodeResponse copy(TraceNodeResponse source, String parentId, String name,
                                   Integer sequence, JsonNode attributes) {
        return new TraceNodeResponse(source.id(), parentId, source.governanceGroupId(), source.type(), name,
                source.status(), source.executionMode(), source.parallelGroup(), sequence, source.iteration(),
                source.provider(), source.model(), source.inputTokens(), source.outputTokens(),
                source.cacheHitTokens(), source.durationMs(), source.errorMessage(), attributes,
                source.startedAt(), source.finishedAt());
    }

    private TraceNodeResponse node(RuleGovernanceTraceSpanEntity row, List<ModelCallRecordEntity> modelCalls) {
        JsonNode attributes = parse(row.getAttributesJson());
        if ("LLM_CALL".equals(row.getSpanType()) || "LLM_TOOL_CALL".equals(row.getSpanType())) {
            ModelCallRecordEntity call = matchingCall(row.getGovernanceGroupId(), row.getIterationNumber(),
                    row.getStartedAt(), row.getFinishedAt(), modelCalls);
            attributes = withModelResponse(attributes, call);
        }
        return new TraceNodeResponse(row.getSpanId(), row.getParentSpanId(), row.getGovernanceGroupId(),
                row.getSpanType(), row.getSpanName(), row.getSpanStatus(), row.getExecutionMode(),
                row.getParallelGroup(), row.getSequenceNo(), row.getIterationNumber(), row.getProviderCode(),
                row.getModelName(), row.getInputTokenCount(), row.getOutputTokenCount(),
                row.getCacheHitTokenCount(), row.getDurationMs(), row.getErrorMessage(),
                attributes, row.getStartedAt(), row.getFinishedAt());
    }

    private List<TraceNodeResponse> legacyNodes(RuleGovernanceRunEntity run, List<ModelCallRecordEntity> modelCalls) {
        List<TraceNodeResponse> nodes = new ArrayList<>();
        String root = "legacy-run-" + run.getId();
        nodes.add(new TraceNodeResponse(root, null, null, "RUN", "反馈治理运行 " + run.getRunNo(),
                run.getStatus().name(), "SERIAL", null, 0, null, null, null,
                run.getInputTokenCount(), run.getOutputTokenCount(), run.getCacheHitTokenCount(), run.getDurationMs(),
                run.getErrorMessage(), object("source", "historical"), run.getStartedAt(), run.getFinishedAt()));

        List<RuleGovernanceEventEntity> events = eventRepository.findByGovernanceRun_IdOrderByCreatedAtAsc(run.getId());
        Map<Long, String> agentParents = new HashMap<>();
        int eventSequence = 1;
        for (RuleGovernanceEventEntity event : events) {
            Long groupId = event.getGovernanceGroup().getId();
            String eventId = "legacy-event-" + event.getId();
            nodes.add(new TraceNodeResponse(eventId, root, groupId, "MESSAGE_CONSUMER",
                    "RabbitMQ 分组分析 #" + groupId + " · 投递 " + (event.getRetryCount() + 1),
                    event.getEventStatus(), "PARALLEL", "governance-run-" + run.getId() + "-groups",
                    eventSequence++, event.getRetryCount() + 1, null, null, 0, 0, 0,
                    elapsed(event.getCreatedAt(), event.getCompletedAt()), event.getErrorMessage(),
                    object("eventId", event.getId()), event.getCreatedAt(), event.getCompletedAt()));
            String agentId = eventId + "-agent";
            nodes.add(new TraceNodeResponse(agentId, eventId, groupId, "AGENT", "反馈治理 Agent",
                    event.getEventStatus(), "SERIAL", null, 1, null, null, null, 0, 0, 0,
                    elapsed(event.getPublishedAt(), event.getCompletedAt()), event.getErrorMessage(),
                    object("derived", true), event.getPublishedAt(), event.getCompletedAt()));
            agentParents.put(groupId, agentId);
        }

        Map<String, List<LlmCallAttemptEntity>> calls = new LinkedHashMap<>();
        for (LlmCallAttemptEntity attempt : attemptRepository.findByGovernanceRunIdOrderById(run.getId())) {
            String key = attempt.getGovernanceGroupId() + "|" + attempt.getOperationType() + "|" + attempt.getChunkIndex();
            calls.computeIfAbsent(key, ignored -> new ArrayList<>()).add(attempt);
        }
        int callSequence = 1;
        for (List<LlmCallAttemptEntity> attempts : calls.values()) {
            LlmCallAttemptEntity first = attempts.get(0);
            Long groupId = first.getGovernanceGroupId();
            String callId = "legacy-llm-call-" + run.getId() + "-" + callSequence;
            boolean success = attempts.stream().anyMatch(value -> "SUCCESS".equals(value.getCallStatus()));
            Instant startedAt = attempts.stream().map(LlmCallAttemptEntity::getCreatedAt).filter(Objects::nonNull).min(Instant::compareTo).orElse(run.getStartedAt());
            Instant finishedAt = attempts.stream().map(LlmCallAttemptEntity::getCreatedAt).filter(Objects::nonNull).max(Instant::compareTo).orElse(startedAt);
            JsonNode callAttributes = withModelResponse(object("derived", true),
                    matchingCall(groupId, first.getChunkIndex(), startedAt, finishedAt, modelCalls));
            nodes.add(new TraceNodeResponse(callId, agentParents.getOrDefault(groupId, root), groupId,
                    "LLM_CALL", (StringUtils.hasText(first.getOperationType()) ? first.getOperationType() : "大模型调用")
                    + (first.getChunkIndex() == null ? "" : " · 第 " + first.getChunkIndex() + " 轮"),
                    success ? "SUCCESS" : "FAILED", "SERIAL", null, callSequence, first.getChunkIndex(),
                    null, null, sumInput(attempts), sumOutput(attempts), sumCache(attempts), sumDuration(attempts),
                    success ? null : attempts.get(attempts.size() - 1).getErrorMessage(), callAttributes,
                    startedAt, finishedAt));
            int attemptSequence = 1;
            for (LlmCallAttemptEntity attempt : attempts) {
                nodes.add(new TraceNodeResponse("legacy-attempt-" + attempt.getId(), callId, groupId,
                        "LLM_ATTEMPT", attempt.getModelName() + " · 尝试 " + attempt.getAttemptOrder(),
                        attempt.getCallStatus(), "SERIAL", null, attemptSequence++, first.getChunkIndex(),
                        attempt.getProviderCode(), attempt.getModelName(), value(attempt.getInputTokenCount()),
                        value(attempt.getOutputTokenCount()), value(attempt.getCacheHitTokenCount()),
                        attempt.getDurationMs(), attempt.getErrorMessage(), object("attemptOrder", attempt.getAttemptOrder()),
                        attempt.getCreatedAt(), attempt.getCreatedAt()));
            }
            callSequence++;
        }

        int toolSequence = 1;
        for (RuleGovernanceToolCallEntity tool : toolRepository.findByGovernanceRun_IdOrderById(run.getId())) {
            Long groupId = tool.getGovernanceGroup().getId();
            nodes.add(new TraceNodeResponse("legacy-tool-" + tool.getId(), agentParents.getOrDefault(groupId, root),
                    groupId, "TOOL_CALL", tool.getToolName(), tool.getCallStatus(), tool.getExecutionMode(), tool.getParallelGroup(),
                    toolSequence++, tool.getIterationNumber(), null, null, 0, 0, 0, tool.getDurationMs(),
                    tool.getErrorMessage(), object("candidateHash", tool.getCandidateHash()), tool.getCreatedAt(), tool.getCreatedAt()));
        }
        if (events.isEmpty() && calls.isEmpty() && run.getCreatedGroupCount() == 0) {
            String message = run.getScannedFeedbackCount() == 0 ? "没有可聚合反馈" : "未创建满足阈值的治理分组";
            nodes.add(new TraceNodeResponse("legacy-outcome-" + run.getId(), root, null, "OUTCOME", message,
                    "NO_OP", "SERIAL", null, 1, null, null, null, 0, 0, 0, 0L,
                    null, object("nextAction", "FAILED/DEFERRED 分组请在治理分组页重新分析"),
                    run.getFinishedAt(), run.getFinishedAt()));
        }
        return nodes;
    }

    private Current current(RuleGovernanceRunEntity run, List<TraceNodeResponse> nodes) {
        Optional<TraceNodeResponse> processing = nodes.stream()
                .filter(node -> "PROCESSING".equals(node.status()) || "RUNNING".equals(node.status()))
                .filter(node -> !"RUN".equals(node.type()))
                .max(Comparator.comparing(node -> node.startedAt() == null ? Instant.EPOCH : node.startedAt()));
        if (processing.isPresent()) return new Current(processing.get().type(), "当前执行：" + processing.get().name());
        if (run.getCreatedGroupCount() == 0) {
            if (run.getScannedFeedbackCount() == 0) return new Current("NO_ELIGIBLE_FEEDBACK",
                    "没有 NEW/PENDING 且尚未归组的反馈；已有 FAILED/DEFERRED 分组不会被顶部聚合重复扫描，请到治理分组中重新分析。");
            return new Current("NO_GROUP_CREATED", "扫描到了反馈，但没有形成满足阈值且可分析的新分组。");
        }
        return new Current(run.getStatus().name(), switch (run.getStatus()) {
            case SUCCESS -> "治理调用链已完成。";
            case FAILED -> "治理调用链执行失败，可展开失败节点查看原因。";
            case PARTIAL_SUCCESS -> "部分分组已完成，仍有失败或暂缓节点。";
            default -> "治理运行等待下一执行节点。";
        });
    }

    private JsonNode parse(String value) { try { return mapper.readTree(StringUtils.hasText(value) ? value : "{}"); } catch (Exception e) { return mapper.createObjectNode(); } }
    private JsonNode withModelResponse(JsonNode attributes, ModelCallRecordEntity call) {
        ObjectNode result = attributes != null && attributes.isObject()
                ? ((ObjectNode) attributes).deepCopy() : mapper.createObjectNode();
        if (call != null) result.set("modelResponse", modelResponse(call));
        return result;
    }
    private ObjectNode modelResponse(ModelCallRecordEntity call) {
        ObjectNode response = mapper.createObjectNode();
        response.put("modelCallRecordId", call.getId());
        response.put("provider", call.getProvider());
        response.put("model", call.getModelName());
        JsonNode structured = parseNested(call.getStructuredResponse());
        if (structured.isObject()) {
            if (structured.path("content").isTextual()) response.put("message", structured.path("content").asText());
            else response.put("message", structured.toString());
            copyText(structured, response, "thoughtSummary");
            copyText(structured, response, "nextAction");
            if (structured.path("toolCalls").isArray()) response.set("toolCalls", structured.path("toolCalls"));
            else if (structured.path("toolName").isTextual()) {
                ArrayNode calls = response.putArray("toolCalls");
                ObjectNode tool = calls.addObject();
                tool.put("callId", "legacy-call"); tool.put("toolName", structured.path("toolName").asText());
                tool.set("arguments", structured.path("arguments"));
            }
        } else if (structured.isTextual()) response.put("message", structured.asText());
        JsonNode raw = parse(call.getRawResponse());
        JsonNode choice = raw.path("choices").path(0);
        if (choice.path("finish_reason").isTextual()) response.put("finishReason", choice.path("finish_reason").asText());
        if (!response.has("message")) response.put("message", StringUtils.hasText(call.getStructuredResponse())
                ? call.getStructuredResponse() : "（模型未返回文本消息）");
        if (!response.has("toolCalls")) response.set("toolCalls", mapper.createArrayNode());
        return response;
    }
    private JsonNode parseNested(String value) {
        JsonNode parsed = parse(value);
        if (parsed.isTextual()) {
            try { return mapper.readTree(parsed.asText()); }
            catch (Exception ignored) { return parsed; }
        }
        return parsed;
    }
    private void copyText(JsonNode source, ObjectNode target, String field) {
        if (source.path(field).isTextual()) target.put(field, source.path(field).asText());
    }
    private ModelCallRecordEntity matchingCall(Long groupId, Integer iteration, Instant startedAt, Instant finishedAt,
                                                List<ModelCallRecordEntity> calls) {
        if (iteration == null || calls == null) return null;
        Instant from = startedAt == null ? Instant.EPOCH : startedAt.minusSeconds(2);
        Instant to = finishedAt == null ? Instant.now().plusSeconds(2) : finishedAt.plusSeconds(2);
        return calls.stream()
                .filter(call -> Objects.equals(groupId, call.getGovernanceGroupId()))
                .filter(call -> Objects.equals(iteration, call.getChunkIndex()))
                .filter(call -> call.getCreatedAt() != null && !call.getCreatedAt().isBefore(from) && !call.getCreatedAt().isAfter(to))
                .min(Comparator.comparingLong(call -> Math.abs(java.time.Duration.between(
                        startedAt == null ? call.getCreatedAt() : startedAt, call.getCreatedAt()).toMillis())))
                .orElse(null);
    }
    private JsonNode object(String key, Object value) { var node = mapper.createObjectNode(); if (value == null) node.putNull(key); else node.set(key, mapper.valueToTree(value)); return node; }
    private int value(Integer value) { return value == null ? 0 : value; }
    private int sumInput(List<LlmCallAttemptEntity> values) { return values.stream().mapToInt(v -> value(v.getInputTokenCount())).sum(); }
    private int sumOutput(List<LlmCallAttemptEntity> values) { return values.stream().mapToInt(v -> value(v.getOutputTokenCount())).sum(); }
    private int sumCache(List<LlmCallAttemptEntity> values) { return values.stream().mapToInt(v -> value(v.getCacheHitTokenCount())).sum(); }
    private long sumDuration(List<LlmCallAttemptEntity> values) { return values.stream().map(LlmCallAttemptEntity::getDurationMs).filter(Objects::nonNull).mapToLong(Long::longValue).sum(); }
    private Long elapsed(Instant start, Instant finish) { return start == null || finish == null ? null : Math.max(0, java.time.Duration.between(start, finish).toMillis()); }
    private record Current(String step, String message) {}
}
