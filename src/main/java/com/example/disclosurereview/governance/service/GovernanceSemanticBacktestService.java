package com.example.disclosurereview.governance.service;

import com.example.disclosurereview.config.FeedbackGovernanceProperties;
import com.example.disclosurereview.governance.domain.RuleCandidate;
import com.example.disclosurereview.llm.EvidenceVerifier;
import com.example.disclosurereview.llm.LlmCallContext;
import com.example.disclosurereview.llm.LlmGateway;
import com.example.disclosurereview.llm.LlmGatewayResponse;
import com.example.disclosurereview.llm.LlmUsage;
import com.example.disclosurereview.model.DocumentPage;
import com.example.disclosurereview.model.ReviewIssue;
import com.example.disclosurereview.persistence.entity.DocumentPageEntity;
import com.example.disclosurereview.persistence.entity.ReviewTaskEntity;
import com.example.disclosurereview.persistence.repository.DocumentPageJpaRepository;
import com.example.disclosurereview.persistence.repository.ReviewTaskJpaRepository;
import com.example.disclosurereview.rule.RuleReviewService;
import com.example.disclosurereview.rule.domain.RuleExecutionStatus;
import com.example.disclosurereview.rule.domain.RuleExecutorType;
import com.example.disclosurereview.strategy.DocumentTypeAliasResolver;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class GovernanceSemanticBacktestService {
    private static final String OPERATION = "FEEDBACK_GOVERNANCE_LLM_BACKTEST";
    private final ReviewTaskJpaRepository taskRepository;
    private final DocumentPageJpaRepository pageRepository;
    private final DocumentTypeAliasResolver documentTypeResolver;
    private final RuleReviewService ruleReviewService;
    private final EvidenceVerifier evidenceVerifier;
    private final LlmGateway llmGateway;
    private final ObjectMapper mapper;
    private final FeedbackGovernanceProperties properties;

    public GovernanceSemanticBacktestService(ReviewTaskJpaRepository taskRepository,
                                             DocumentPageJpaRepository pageRepository,
                                             DocumentTypeAliasResolver documentTypeResolver,
                                             RuleReviewService ruleReviewService,
                                             EvidenceVerifier evidenceVerifier,
                                             LlmGateway llmGateway,
                                             ObjectMapper mapper,
                                             FeedbackGovernanceProperties properties) {
        this.taskRepository = taskRepository;
        this.pageRepository = pageRepository;
        this.documentTypeResolver = documentTypeResolver;
        this.ruleReviewService = ruleReviewService;
        this.evidenceVerifier = evidenceVerifier;
        this.llmGateway = llmGateway;
        this.mapper = mapper;
        this.properties = properties;
    }

    public SemanticBacktestOutcome run(RuleCandidate candidate,
                                       List<RuleBacktestSampleService.BacktestSample> samples,
                                       BacktestCallScope callScope) {
        Map<String, SampleState> states = new LinkedHashMap<>();
        Map<Long, SampleDocument> documents = new LinkedHashMap<>();
        Map<Long, List<ReviewIssue>> hybridCandidates = new LinkedHashMap<>();
        List<Segment> allSegments = new ArrayList<>();
        double minimumConfidence = candidate.condition().path("minConfidence").isNumber()
                ? candidate.condition().path("minConfidence").asDouble()
                : candidate.executorType() == RuleExecutorType.HYBRID ? 0.8 : 0.75;
        for (RuleBacktestSampleService.BacktestSample sample : samples) {
            SampleDocument document = documents.computeIfAbsent(sample.taskId(), this::load);
            Boolean applies = applies(candidate.scope(), document.task());
            if (applies == null) {
                states.put(sample.sampleId(), SampleState.immediate(null, RuleExecutionStatus.INDETERMINATE,
                        "无法确定 productTypes 适用范围"));
                continue;
            }
            if (!applies) {
                states.put(sample.sampleId(), SampleState.immediate(false, RuleExecutionStatus.SKIPPED,
                        "候选规则不适用于该样本"));
                continue;
            }
            List<Segment> segments = candidate.executorType() == RuleExecutorType.HYBRID
                    ? hybridSegments(document, sample, hybridCandidates.computeIfAbsent(sample.taskId(),
                            ignored -> locateHybridCandidates(candidate, document)))
                    : policySegments(document, sample);
            if (segments.isEmpty()) {
                Boolean matched = candidate.executorType() == RuleExecutorType.HYBRID ? false : null;
                states.put(sample.sampleId(), SampleState.immediate(matched,
                        matched == null ? RuleExecutionStatus.INDETERMINATE : RuleExecutionStatus.NOT_HIT,
                        matched == null ? "DOCUMENT_TEXT_EMPTY" : "HYBRID_LOCATOR_NO_CANDIDATE"));
                continue;
            }
            SampleState state = new SampleState(segments.size());
            states.put(sample.sampleId(), state);
            allSegments.addAll(segments);
        }

        int calls = 0;
        long inputTokens = 0, outputTokens = 0, cacheTokens = 0;
        List<String> failures = new ArrayList<>();
        int batchIndex = 0;
        for (List<Segment> batch : batches(candidate, allSegments)) {
            batchIndex++;
            calls++;
            List<Long> taskIds = batch.stream().map(Segment::taskId).distinct().toList();
            LlmCallContext context = LlmCallContext.governanceBacktest(
                    callScope == null ? null : callScope.governanceRunId(),
                    callScope == null ? null : callScope.governanceGroupId(),
                    candidate.ruleCode(), batchIndex, taskIds, properties.getBacktest().getPromptVersion());
            try {
                LlmGatewayResponse<Map<String, SegmentDecision>> response = llmGateway.chatCompletion(
                        context, systemPrompt(), userPrompt(candidate, batch), raw -> parseResponse(raw, batch));
                inputTokens += token(response.usage(), true, false);
                outputTokens += token(response.usage(), false, false);
                cacheTokens += token(response.usage(), false, true);
                for (Segment segment : batch) {
                    SegmentDecision decision = response.result().get(segment.segmentId());
                    states.get(segment.sampleId()).accept(validate(segment, decision,
                            minimumConfidence, documents.get(segment.taskId())));
                }
            } catch (RuntimeException e) {
                failures.add("batch=" + batchIndex + ": " + safe(e.getMessage()));
                for (Segment segment : batch) {
                    states.get(segment.sampleId()).accept(SegmentResult.indeterminate("LLM_BATCH_FAILED: " + safe(e.getMessage())));
                }
            }
        }

        Map<String, SemanticSampleResult> results = new LinkedHashMap<>();
        states.forEach((sampleId, state) -> results.put(sampleId, state.finish()));
        return new SemanticBacktestOutcome(Map.copyOf(results), calls, inputTokens, outputTokens,
                cacheTokens, List.copyOf(failures), OPERATION);
    }

    private SampleDocument load(Long taskId) {
        ReviewTaskEntity task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("回测任务不存在: " + taskId));
        List<DocumentPage> pages = pageRepository.findByTaskIdOrderByPageNumber(taskId).stream()
                .map(this::page).toList();
        return new SampleDocument(task, pages);
    }

    private List<Segment> policySegments(SampleDocument document,
                                         RuleBacktestSampleService.BacktestSample sample) {
        if (StringUtils.hasText(sample.targetEvidenceText())) {
            int page = sample.targetPageNumber() == null ? 0 : sample.targetPageNumber();
            String text = "【原问题页码】" + page + "\n【原问题证据】" + sample.targetEvidenceText();
            return List.of(new Segment(sample.sampleId(), sample.taskId(), id(sample.sampleId(), 1),
                    page, page, text, null));
        }
        int maximum = Math.max(500, Math.min(properties.getBacktest().getSampleWindowChars(),
                properties.getBacktest().getMaximumRequestChars()));
        int overlap = Math.min(properties.getBacktest().getWindowOverlapChars(), maximum - 1);
        List<PagePiece> pieces = new ArrayList<>();
        for (DocumentPage page : document.pages()) {
            String text = page.normalizedText();
            if (!StringUtils.hasText(text)) continue;
            int offset = 0;
            while (offset < text.length()) {
                int end = Math.min(text.length(), offset + maximum);
                pieces.add(new PagePiece(page.pageNumber(), text.substring(offset, end)));
                if (end >= text.length()) break;
                offset = Math.max(offset + 1, end - overlap);
            }
        }
        List<Segment> segments = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        int index = 1;
        int pageFrom = 0, pageTo = 0;
        for (PagePiece piece : pieces) {
            String rendered = "【第" + piece.pageNumber() + "页】\n" + piece.text();
            if (!text.isEmpty() && text.length() + rendered.length() + 1 > maximum) {
                segments.add(new Segment(sample.sampleId(), document.task().getId(), id(sample.sampleId(), index++),
                        pageFrom, pageTo, text.toString(), null));
                text.setLength(0);
            }
            if (text.isEmpty()) pageFrom = piece.pageNumber();
            pageTo = piece.pageNumber();
            if (!text.isEmpty()) text.append('\n');
            text.append(rendered);
        }
        if (!text.isEmpty()) {
            segments.add(new Segment(sample.sampleId(), document.task().getId(), id(sample.sampleId(), index),
                    pageFrom, pageTo, text.toString(), null));
        }
        return List.copyOf(segments);
    }

    private List<ReviewIssue> locateHybridCandidates(RuleCandidate candidate, SampleDocument document) {
        String locator = candidate.condition().path("locator").asText(candidate.ruleCode());
        Set<String> enabled = switch (locator) {
            case RuleReviewService.RULE_CONTENT_PRODUCT_CODE_CONFLICT -> Set.of(
                    RuleReviewService.RULE_PRODUCT_CODE_EXTRACTION,
                    RuleReviewService.RULE_CONTENT_PRODUCT_CODE_CONFLICT);
            case RuleReviewService.RULE_POSSIBLE_TEMPLATE_RESIDUE -> Set.of(
                    RuleReviewService.RULE_PRODUCT_CODE_EXTRACTION,
                    RuleReviewService.RULE_POSSIBLE_TEMPLATE_RESIDUE);
            default -> Set.of(locator);
        };
        return ruleReviewService.review(document.pages(),
                documentTypeResolver.resolve(document.task().getDeclaredDocumentType()),
                document.task().getDeclaredProductCode(), null, enabled).issues();
    }

    private List<Segment> hybridSegments(SampleDocument document,
                                         RuleBacktestSampleService.BacktestSample sample,
                                         List<ReviewIssue> locatedIssues) {
        List<ReviewIssue> issues = locatedIssues;
        if (StringUtils.hasText(sample.targetEvidenceText())) {
            ReviewIssue closest = closestIssue(issues, sample.targetPageNumber(), sample.targetEvidenceText());
            if (closest == null) return List.of();
            issues = List.of(closest);
        }
        List<Segment> result = new ArrayList<>();
        int index = 1;
        for (ReviewIssue issue : issues) {
            int page = issue.pageNumber() == null ? 0 : issue.pageNumber();
            String body = "【候选页码】" + page + "\n【候选证据】" + nullToEmpty(issue.evidenceText())
                    + "\n【候选说明】" + nullToEmpty(issue.explanation());
            result.add(new Segment(sample.sampleId(), document.task().getId(), id(sample.sampleId(), index++),
                    page, page, body, issue));
        }
        return List.copyOf(result);
    }

    private ReviewIssue closestIssue(List<ReviewIssue> issues, Integer targetPage, String targetEvidence) {
        String normalizedTarget = compact(targetEvidence);
        ReviewIssue best = null;
        int bestScore = -1;
        for (ReviewIssue issue : issues) {
            int score = 0;
            if (targetPage != null && targetPage.equals(issue.pageNumber())) score += 10_000;
            String evidence = compact(issue.evidenceText());
            if (!normalizedTarget.isEmpty() && normalizedTarget.equals(evidence)) score += 100_000;
            else if (!normalizedTarget.isEmpty() && !evidence.isEmpty()
                    && (normalizedTarget.contains(evidence) || evidence.contains(normalizedTarget))) {
                score += Math.min(normalizedTarget.length(), evidence.length());
            }
            if (score > bestScore) {
                best = issue;
                bestScore = score;
            }
        }
        return bestScore > 0 ? best : null;
    }

    private List<List<Segment>> batches(RuleCandidate candidate, List<Segment> segments) {
        int maximum = properties.getBacktest().getMaximumRequestChars();
        List<List<Segment>> result = new ArrayList<>();
        List<Segment> current = new ArrayList<>();
        for (Segment segment : segments) {
            List<Segment> trial = new ArrayList<>(current);
            trial.add(segment);
            if (!current.isEmpty() && userPrompt(candidate, trial).length() > maximum) {
                result.add(List.copyOf(current));
                current.clear();
            }
            current.add(segment);
        }
        if (!current.isEmpty()) result.add(List.copyOf(current));
        return List.copyOf(result);
    }

    private String systemPrompt() {
        return "你是银行信息披露规则治理回测执行器。逐段独立判断，只返回合法 JSON，禁止省略任何 segmentId。";
    }

    private String userPrompt(RuleCandidate candidate, List<Segment> batch) {
        var root = mapper.createObjectNode();
        root.put("ruleCode", candidate.ruleCode());
        root.put("executorType", candidate.executorType().name());
        root.set("condition", candidate.condition());
        root.set("prompt", candidate.prompt());
        var samples = root.putArray("segments");
        for (Segment segment : batch) {
            var row = samples.addObject();
            row.put("sampleId", segment.sampleId());
            row.put("taskId", segment.taskId());
            row.put("segmentId", segment.segmentId());
            row.put("pageFrom", segment.pageFrom());
            row.put("pageTo", segment.pageTo());
            row.put("text", segment.text());
        }
        try {
            return "请按规则目标判断每个文本段是否违规。低置信度也必须如实返回。输出固定结构："
                    + "{\"results\":[{\"taskId\":1,\"segmentId\":\"1-1\",\"violated\":false,"
                    + "\"confidence\":0.9,\"pageNumber\":1,\"evidenceText\":\"\",\"explanation\":\"\"}]}。\n"
                    + mapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("无法构造语义回测请求", e);
        }
    }

    private Map<String, SegmentDecision> parseResponse(String raw, List<Segment> expected) {
        try {
            JsonNode root = mapper.readTree(stripFence(raw));
            JsonNode rows = root.path("results");
            if (!rows.isArray()) throw new IllegalArgumentException("LLM_RESPONSE_MISSING_RESULTS");
            Set<String> expectedIds = expected.stream().map(Segment::segmentId)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            Map<String, SegmentDecision> result = new LinkedHashMap<>();
            for (JsonNode row : rows) {
                String segmentId = row.path("segmentId").asText(null);
                if (!expectedIds.contains(segmentId) || !row.has("violated") || !row.path("violated").isBoolean()) continue;
                Long taskId = row.path("taskId").isIntegralNumber() ? row.path("taskId").asLong() : null;
                Segment expectedSegment = expected.stream().filter(segment -> segment.segmentId().equals(segmentId))
                        .findFirst().orElse(null);
                if (expectedSegment == null || !expectedSegment.taskId().equals(taskId)) continue;
                result.putIfAbsent(segmentId, new SegmentDecision(
                        taskId,
                        segmentId, row.path("violated").asBoolean(), confidence(row.path("confidence")),
                        row.path("pageNumber").isIntegralNumber() ? row.path("pageNumber").asInt() : null,
                        text(row, "evidenceText"), text(row, "explanation")));
            }
            if (!expected.isEmpty() && result.isEmpty()) {
                throw new IllegalArgumentException("LLM_RESPONSE_NO_VALID_RESULTS");
            }
            return Map.copyOf(result);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("LLM_BACKTEST_INVALID_JSON: " + e.getMessage(), e);
        }
    }

    private SegmentResult validate(Segment segment,
                                   SegmentDecision decision,
                                   double minimumConfidence,
                                   SampleDocument document) {
        if (decision == null || !segment.taskId().equals(decision.taskId())) {
            return SegmentResult.indeterminate("LLM_RESPONSE_MISSING_SEGMENT: " + segment.segmentId());
        }
        if (decision.confidence() < minimumConfidence) {
            return SegmentResult.indeterminate("LOW_CONFIDENCE: " + decision.confidence());
        }
        if (!decision.violated()) {
            return new SegmentResult(false, RuleExecutionStatus.NOT_HIT, "模型明确判定未命中",
                    decision.pageNumber(), null, decision.explanation());
        }
        if (!evidenceVerifier.verifyText(decision.pageNumber(), decision.evidenceText(), document.pages())) {
            return SegmentResult.indeterminate("EVIDENCE_NOT_VERIFIED");
        }
        return new SegmentResult(true, RuleExecutionStatus.HIT, "语义回测命中",
                decision.pageNumber(), decision.evidenceText(), decision.explanation());
    }

    private Boolean applies(JsonNode scope, ReviewTaskEntity task) {
        if (scope == null || !scope.isObject()) return true;
        if (!matches(scope.path("documentCategories"), task.getDocumentCategory() == null ? null : task.getDocumentCategory().name())) return false;
        var resolvedType = documentTypeResolver.resolve(task.getDeclaredDocumentType());
        if (!matchesAny(scope.path("documentTypes"), task.getDeclaredDocumentType(),
                resolvedType == null ? null : resolvedType.name())) return false;
        if (!matches(scope.path("productCodes"), task.getDeclaredProductCode())) return false;
        if (scope.path("productTypes").isArray() && !scope.path("productTypes").isEmpty()) return null;
        return true;
    }

    private boolean matches(JsonNode configured, String actual) {
        if (!configured.isArray() || configured.isEmpty()) return true;
        if (!StringUtils.hasText(actual)) return false;
        String normalized = normalize(actual);
        for (JsonNode value : configured) if (normalized.equals(normalize(value.asText()))) return true;
        return false;
    }

    private boolean matchesAny(JsonNode configured, String... actualValues) {
        if (!configured.isArray() || configured.isEmpty()) return true;
        for (JsonNode expected : configured) {
            String value = expected.asText("").toLowerCase(Locale.ROOT);
            for (String actual : actualValues) {
                if (StringUtils.hasText(actual) && actual.toLowerCase(Locale.ROOT).contains(value)) return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        String normalized = value == null ? "" : value.strip().toUpperCase(Locale.ROOT);
        return "AGREEMENT".equals(normalized) ? "PROTOCOL" : normalized;
    }

    private DocumentPage page(DocumentPageEntity row) {
        return new DocumentPage(row.getPageNumber(), row.getRawText(), row.getNormalizedText());
    }

    private String id(String sampleId, int index) { return sampleId + "-" + index; }
    private String text(JsonNode root, String field) {
        return root.has(field) && !root.path(field).isNull() ? root.path(field).asText(null) : null;
    }
    private double confidence(JsonNode node) {
        if (!node.isNumber()) return 0;
        double value = node.asDouble();
        return value > 1 && value <= 100 ? value / 100 : Math.max(0, Math.min(1, value));
    }
    private String stripFence(String value) {
        String text = value == null ? "" : value.strip();
        if (text.startsWith("```")) {
            int firstLine = text.indexOf('\n');
            int lastFence = text.lastIndexOf("```");
            if (firstLine >= 0 && lastFence > firstLine) text = text.substring(firstLine + 1, lastFence).strip();
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        return start >= 0 && end >= start ? text.substring(start, end + 1) : text;
    }
    private String compact(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "");
    }
    private long token(LlmUsage usage, boolean input, boolean cache) {
        if (usage == null) return 0;
        Integer value = cache ? usage.cacheHitTokens() : input ? usage.inputTokens() : usage.outputTokens();
        return value == null ? 0 : value;
    }
    private String safe(String value) { return value == null ? "unknown" : value.length() > 500 ? value.substring(0, 500) : value; }
    private String nullToEmpty(String value) { return value == null ? "" : value; }

    public record BacktestCallScope(Long governanceRunId, Long governanceGroupId, Integer iterationNumber) {}

    public record SemanticBacktestOutcome(Map<String, SemanticSampleResult> results,
                                          int llmCallCount,
                                          long inputTokens,
                                          long outputTokens,
                                          long cacheHitTokens,
                                          List<String> failures,
                                          String operationType) {}

    public record SemanticSampleResult(Boolean matched,
                                       RuleExecutionStatus status,
                                       String detail,
                                       Integer pageNumber,
                                       String evidenceText,
                                       String explanation,
                                       int segmentCount) {}

    private record SampleDocument(ReviewTaskEntity task, List<DocumentPage> pages) {}
    private record PagePiece(int pageNumber, String text) {}
    private record Segment(String sampleId, Long taskId, String segmentId,
                           int pageFrom, int pageTo, String text, ReviewIssue candidate) {}
    private record SegmentDecision(Long taskId, String segmentId, boolean violated, double confidence,
                                   Integer pageNumber, String evidenceText, String explanation) {}
    private record SegmentResult(Boolean matched, RuleExecutionStatus status, String detail,
                                 Integer pageNumber, String evidenceText, String explanation) {
        private static SegmentResult indeterminate(String detail) {
            return new SegmentResult(null, RuleExecutionStatus.INDETERMINATE, detail, null, null, null);
        }
    }

    private static final class SampleState {
        private final int expected;
        private final List<SegmentResult> results = new ArrayList<>();
        private SemanticSampleResult immediate;

        private SampleState(int expected) { this.expected = expected; }
        private static SampleState immediate(Boolean matched, RuleExecutionStatus status, String detail) {
            SampleState state = new SampleState(0);
            state.immediate = new SemanticSampleResult(matched, status, detail, null, null, null, 0);
            return state;
        }
        private void accept(SegmentResult result) { results.add(result); }
        private SemanticSampleResult finish() {
            if (immediate != null) return immediate;
            SegmentResult hit = results.stream().filter(row -> Boolean.TRUE.equals(row.matched())).findFirst().orElse(null);
            if (hit != null) return new SemanticSampleResult(true, RuleExecutionStatus.HIT, hit.detail(),
                    hit.pageNumber(), hit.evidenceText(), hit.explanation(), expected);
            boolean allClear = results.size() == expected && results.stream().allMatch(row -> Boolean.FALSE.equals(row.matched()));
            if (allClear) {
                SegmentResult first = results.isEmpty() ? null : results.get(0);
                return new SemanticSampleResult(false, RuleExecutionStatus.NOT_HIT, "所有窗口均明确未命中",
                        first == null ? null : first.pageNumber(), null,
                        first == null ? null : first.explanation(), expected);
            }
            String detail = results.stream().filter(row -> row.matched() == null).map(SegmentResult::detail)
                    .distinct().reduce((left, right) -> left + "; " + right).orElse("SEMANTIC_RESULT_INCOMPLETE");
            return new SemanticSampleResult(null, RuleExecutionStatus.INDETERMINATE, detail,
                    null, null, null, expected);
        }
    }
}
