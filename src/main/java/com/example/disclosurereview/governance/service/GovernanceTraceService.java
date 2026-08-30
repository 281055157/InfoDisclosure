package com.example.disclosurereview.governance.service;

import com.example.disclosurereview.governance.persistence.entity.RuleGovernanceRunEntity;
import com.example.disclosurereview.governance.persistence.entity.RuleGovernanceTraceSpanEntity;
import com.example.disclosurereview.governance.persistence.repository.RuleGovernanceRunJpaRepository;
import com.example.disclosurereview.governance.persistence.repository.RuleGovernanceTraceSpanJpaRepository;
import com.example.disclosurereview.llm.LlmUsage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;

@Service
public class GovernanceTraceService {
    private static final Logger log = LoggerFactory.getLogger(GovernanceTraceService.class);
    private final RuleGovernanceTraceSpanJpaRepository spanRepository;
    private final RuleGovernanceRunJpaRepository runRepository;
    private final ObjectMapper mapper;
    private final ThreadLocal<Deque<String>> activeSpans = ThreadLocal.withInitial(ArrayDeque::new);

    public GovernanceTraceService(RuleGovernanceTraceSpanJpaRepository spanRepository,
                                  RuleGovernanceRunJpaRepository runRepository,
                                  ObjectMapper mapper) {
        this.spanRepository = spanRepository;
        this.runRepository = runRepository;
        this.mapper = mapper;
    }

    public String ensureRoot(RuleGovernanceRunEntity run) {
        String traceId = StringUtils.hasText(run.getTraceId()) ? run.getTraceId() : "governance-run-" + run.getId();
        if (!StringUtils.hasText(run.getTraceId())) {
            run.setTraceId(traceId);
            runRepository.save(run);
        }
        String rootId = rootSpanId(run.getId());
        if (spanRepository.findBySpanId(rootId).isPresent()) return rootId;
        Instant now = run.getStartedAt() == null ? Instant.now() : run.getStartedAt();
        RuleGovernanceTraceSpanEntity root = base(run.getId(), null, traceId, rootId, null,
                "RUN", "反馈治理运行 " + run.getRunNo(), "SERIAL", null, 0, null, now);
        try { spanRepository.save(root); }
        catch (RuntimeException race) {
            if (spanRepository.findBySpanId(rootId).isEmpty()) log.debug("Unable to initialize governance trace root: {}", race.getMessage());
        }
        return rootId;
    }

    public SpanScope open(Long runId,
                          Long groupId,
                          String type,
                          String name,
                          String executionMode,
                          String parallelGroup,
                          Integer sequence,
                          Integer iteration,
                          String provider,
                          String model,
                          Map<String, ?> attributes) {
        return openWithParent(runId, groupId, null, type, name, executionMode, parallelGroup,
                sequence, iteration, provider, model, attributes);
    }

    public SpanScope openWithParent(Long runId,
                                    Long groupId,
                                    String explicitParentSpanId,
                                    String type,
                                    String name,
                                    String executionMode,
                                    String parallelGroup,
                                    Integer sequence,
                                    Integer iteration,
                                    String provider,
                                    String model,
                                    Map<String, ?> attributes) {
        if (runId == null) return SpanScope.noop();
        try {
            RuleGovernanceRunEntity run = runRepository.findById(runId).orElseThrow();
            String rootId = ensureRoot(run);
            String parent = StringUtils.hasText(explicitParentSpanId) ? explicitParentSpanId : activeSpans.get().peek();
            if (!StringUtils.hasText(parent)) parent = rootId;
            String spanId = type.toLowerCase() + "-" + UUID.randomUUID();
            Instant now = Instant.now();
            RuleGovernanceTraceSpanEntity entity = base(runId, groupId, run.getTraceId(), spanId, parent,
                    type, name, executionMode, parallelGroup, sequence, iteration, now);
            entity.setProviderCode(provider);
            entity.setModelName(model);
            entity.setAttributesJson(attributes == null || attributes.isEmpty() ? null : mapper.writeValueAsString(attributes));
            spanRepository.save(entity);
            activeSpans.get().push(spanId);
            return new SpanScope(this, spanId, true);
        } catch (Exception e) {
            log.debug("Unable to start governance trace span {}: {}", type, e.getMessage());
            return SpanScope.noop();
        }
    }

    public String currentSpanId() {
        return activeSpans.get().peek();
    }

    public void instant(Long runId, Long groupId, String type, String name, String status,
                        String executionMode, String parallelGroup, Map<String, ?> attributes) {
        SpanScope scope = open(runId, groupId, type, name, executionMode, parallelGroup,
                null, null, null, null, attributes);
        try { scope.finish(status, LlmUsage.empty(), null); }
        finally { scope.close(); }
    }

    public void finishRoot(Long runId, String status, String error) {
        if (runId == null) return;
        finish(rootSpanId(runId), status, LlmUsage.empty(), error);
    }

    public void reopenRoot(Long runId) {
        if (runId == null) return;
        try {
            RuleGovernanceRunEntity run = runRepository.findById(runId).orElseThrow();
            RuleGovernanceTraceSpanEntity root = spanRepository.findBySpanId(ensureRoot(run)).orElseThrow();
            root.setSpanStatus("PROCESSING");
            root.setFinishedAt(null);
            root.setDurationMs(null);
            root.setErrorMessage(null);
            root.setUpdatedAt(Instant.now());
            spanRepository.save(root);
        } catch (RuntimeException e) {
            log.debug("Unable to reopen governance trace root {}: {}", runId, e.getMessage());
        }
    }

    private RuleGovernanceTraceSpanEntity base(Long runId, Long groupId, String traceId,
                                                String spanId, String parentId, String type, String name,
                                                String mode, String parallelGroup, Integer sequence,
                                                Integer iteration, Instant now) {
        RuleGovernanceTraceSpanEntity entity = new RuleGovernanceTraceSpanEntity();
        entity.setGovernanceRunId(runId);
        entity.setGovernanceGroupId(groupId);
        entity.setTraceId(traceId);
        entity.setSpanId(spanId);
        entity.setParentSpanId(parentId);
        entity.setSpanType(type);
        entity.setSpanName(name);
        entity.setExecutionMode(StringUtils.hasText(mode) ? mode : "SERIAL");
        entity.setParallelGroup(parallelGroup);
        entity.setSequenceNo(sequence);
        entity.setIterationNumber(iteration);
        entity.setSpanStatus("PROCESSING");
        entity.setStartedAt(now);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return entity;
    }

    private void finish(String spanId, String status, LlmUsage usage, String error) {
        if (!StringUtils.hasText(spanId)) return;
        try {
            RuleGovernanceTraceSpanEntity entity = spanRepository.findBySpanId(spanId).orElse(null);
            if (entity == null) return;
            Instant now = Instant.now();
            entity.setSpanStatus(StringUtils.hasText(status) ? status : "SUCCESS");
            entity.setFinishedAt(now);
            entity.setDurationMs(Math.max(0, Duration.between(entity.getStartedAt(), now).toMillis()));
            LlmUsage safe = usage == null ? LlmUsage.empty() : usage;
            entity.setInputTokenCount(safe.inputTokens());
            entity.setOutputTokenCount(safe.outputTokens());
            entity.setCacheHitTokenCount(safe.cacheHitTokens());
            entity.setErrorMessage(safeError(error));
            entity.setUpdatedAt(now);
            spanRepository.save(entity);
        } catch (RuntimeException e) {
            log.debug("Unable to finish governance trace span {}: {}", spanId, e.getMessage());
        }
    }

    private void pop(String spanId) {
        Deque<String> stack = activeSpans.get();
        if (!stack.isEmpty() && spanId.equals(stack.peek())) stack.pop();
        else stack.remove(spanId);
        if (stack.isEmpty()) activeSpans.remove();
    }

    private String rootSpanId(Long runId) { return "governance-run-root-" + runId; }
    private String safeError(String value) {
        if (!StringUtils.hasText(value)) return null;
        return value.length() > 2000 ? value.substring(0, 2000) : value;
    }

    public static final class SpanScope implements AutoCloseable {
        private final GovernanceTraceService service;
        private final String spanId;
        private final boolean active;
        private boolean closed;

        private SpanScope(GovernanceTraceService service, String spanId, boolean active) {
            this.service = service; this.spanId = spanId; this.active = active;
        }

        public static SpanScope noop() { return new SpanScope(null, null, false); }
        public String spanId() { return spanId; }
        public void success() { finish("SUCCESS", LlmUsage.empty(), null); }
        public void success(LlmUsage usage) { finish("SUCCESS", usage, null); }
        public void fail(Throwable error) { finish("FAILED", LlmUsage.empty(), error == null ? null : error.getMessage()); }
        public void finish(String status, LlmUsage usage, String error) {
            if (active) service.finish(spanId, status, usage, error);
        }
        @Override public void close() {
            if (closed) return;
            closed = true;
            if (active) service.pop(spanId);
        }
    }
}
