package com.example.disclosurereview.governance.service;

import com.example.disclosurereview.config.FeedbackGovernanceProperties;
import com.example.disclosurereview.governance.domain.CandidateValidationResult;
import com.example.disclosurereview.governance.domain.RuleCandidate;
import com.example.disclosurereview.governance.domain.BacktestExecutionStatus;
import com.example.disclosurereview.governance.domain.BacktestRiskLevel;
import com.example.disclosurereview.governance.domain.RuleBacktestResult;
import com.example.disclosurereview.governance.persistence.entity.RuleFeedbackGovernanceGroupEntity;
import com.example.disclosurereview.governance.persistence.repository.RuleFeedbackGovernanceGroupJpaRepository;
import com.example.disclosurereview.llm.EvidenceVerifier;
import com.example.disclosurereview.llm.LlmCallContext;
import com.example.disclosurereview.llm.LlmGateway;
import com.example.disclosurereview.llm.LlmGatewayResponse;
import com.example.disclosurereview.llm.LlmUsage;
import com.example.disclosurereview.model.DocumentCategory;
import com.example.disclosurereview.persistence.entity.DocumentPageEntity;
import com.example.disclosurereview.persistence.entity.ReviewTaskEntity;
import com.example.disclosurereview.persistence.repository.DocumentPageJpaRepository;
import com.example.disclosurereview.persistence.repository.ReviewTaskJpaRepository;
import com.example.disclosurereview.rule.RuleReviewService;
import com.example.disclosurereview.rule.domain.RuleExecutionStatus;
import com.example.disclosurereview.rule.domain.RuleExecutorType;
import com.example.disclosurereview.strategy.DocumentTypeAliasResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GovernanceSemanticBacktestServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void fiveShortSamplesUseOneBatchedModelRequestAndKeepTaskIdsOutOfPrimaryContext() {
        Fixture fixture = fixture(30_000, 6_000, false, 5);

        var outcome = fixture.service().run(candidate(), fixture.samples(),
                new GovernanceSemanticBacktestService.BacktestCallScope(11L, 22L, 3));

        assertThat(outcome.llmCallCount()).isEqualTo(1);
        assertThat(outcome.inputTokens()).isEqualTo(100);
        assertThat(outcome.outputTokens()).isEqualTo(20);
        assertThat(outcome.cacheHitTokens()).isEqualTo(7);
        assertThat(outcome.results()).hasSize(5).allSatisfy((taskId, result) -> {
            assertThat(result.matched()).isFalse();
            assertThat(result.segmentCount()).isEqualTo(1);
        });
        ArgumentCaptor<LlmCallContext> context = ArgumentCaptor.forClass(LlmCallContext.class);
        verify(fixture.gateway(), times(1)).chatCompletion(context.capture(), anyString(), anyString(), any());
        assertThat(context.getValue().taskId()).isNull();
        assertThat(context.getValue().governanceRunId()).isEqualTo(11L);
        assertThat(context.getValue().governanceGroupId()).isEqualTo(22L);
        assertThat(context.getValue().operationType()).isEqualTo("FEEDBACK_GOVERNANCE_LLM_BACKTEST");
        assertThat(context.getValue().relatedTaskIds()).containsExactly(1L, 2L, 3L, 4L, 5L);
    }

    @Test
    void fiveFeedbackSamplesOnSameTaskAreEvaluatedIndependentlyWithoutRepeatingWholeDocument() {
        ReviewTaskJpaRepository tasks = mock(ReviewTaskJpaRepository.class);
        DocumentPageJpaRepository pages = mock(DocumentPageJpaRepository.class);
        DocumentTypeAliasResolver aliases = mock(DocumentTypeAliasResolver.class);
        RuleReviewService ruleReviewService = mock(RuleReviewService.class);
        LlmGateway gateway = mock(LlmGateway.class);
        FeedbackGovernanceProperties properties = new FeedbackGovernanceProperties();
        ReviewTaskEntity task = task(14L);
        when(tasks.findById(14L)).thenReturn(Optional.of(task));
        when(pages.findByTaskIdOrderByPageNumber(14L)).thenReturn(List.of(page(task,
                "不应在证据定向回测中重复发送的完整文档正文")));

        List<RuleBacktestSampleService.BacktestSample> samples = new ArrayList<>();
        StringBuilder response = new StringBuilder("{\"results\":[");
        for (long id = 1; id <= 5; id++) {
            samples.add(new RuleBacktestSampleService.BacktestSample("feedback:" + id, 14L, id, 100L + id,
                    "FALSE_POSITIVE", true, (int) id, "否定语境证据-" + id));
            if (id > 1) response.append(',');
            response.append("{\"taskId\":14,\"segmentId\":\"feedback:").append(id)
                    .append("-1\",\"violated\":false,\"confidence\":0.95,\"pageNumber\":")
                    .append(id).append(",\"evidenceText\":\"\",\"explanation\":\"否定语境\"}");
        }
        response.append("]}");
        when(gateway.chatCompletion(any(LlmCallContext.class), anyString(), anyString(), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Function<String, Object> handler = invocation.getArgument(3);
            Object parsed = handler.apply(response.toString());
            return new LlmGatewayResponse<>(parsed, null, "provider", "model",
                    new LlmUsage(50, 10, 0, "{}"));
        });
        GovernanceSemanticBacktestService service = new GovernanceSemanticBacktestService(tasks, pages, aliases,
                ruleReviewService, new EvidenceVerifier(), gateway, mapper, properties);

        var outcome = service.run(candidate(), samples,
                new GovernanceSemanticBacktestService.BacktestCallScope(11L, 22L, 3));

        assertThat(outcome.llmCallCount()).isEqualTo(1);
        assertThat(outcome.results()).hasSize(5).allSatisfy((sampleId, result) ->
                assertThat(result.matched()).isFalse());
        ArgumentCaptor<String> userPrompt = ArgumentCaptor.forClass(String.class);
        verify(gateway).chatCompletion(any(LlmCallContext.class), anyString(), userPrompt.capture(), any());
        assertThat(userPrompt.getValue()).contains("feedback:1", "否定语境证据-1", "feedback:5", "否定语境证据-5")
                .doesNotContain("不应在证据定向回测中重复发送的完整文档正文");
        verify(tasks, times(1)).findById(14L);
        verify(pages, times(1)).findByTaskIdOrderByPageNumber(14L);
    }

    @Test
    void ruleBacktestCountsFeedbackSamplesSeparatelyFromUniqueDocuments() {
        RuleFeedbackGovernanceGroupJpaRepository groups = mock(RuleFeedbackGovernanceGroupJpaRepository.class);
        RuleBacktestSampleService sampleService = mock(RuleBacktestSampleService.class);
        RuleExecutionSandbox sandbox = mock(RuleExecutionSandbox.class);
        RuleCandidateValidationService validation = mock(RuleCandidateValidationService.class);
        GovernanceSemanticBacktestService semantic = mock(GovernanceSemanticBacktestService.class);
        FeedbackGovernanceProperties properties = new FeedbackGovernanceProperties();
        RuleFeedbackGovernanceGroupEntity group = new RuleFeedbackGovernanceGroupEntity();
        group.setRuleCode("OLD_REGEX");
        when(groups.findById(6L)).thenReturn(Optional.of(group));
        RuleCandidate candidate = candidate();
        when(validation.validate(candidate, "OLD_REGEX", true))
                .thenReturn(new CandidateValidationResult(true, "candidate-hash", List.of(), List.of(), List.of()));

        List<RuleBacktestSampleService.BacktestSample> samples = new ArrayList<>();
        Map<String, GovernanceSemanticBacktestService.SemanticSampleResult> decisions = new LinkedHashMap<>();
        for (long id = 1; id <= 5; id++) {
            String sampleId = "feedback:" + id;
            samples.add(new RuleBacktestSampleService.BacktestSample(sampleId, 14L, id, 100L + id,
                    "FALSE_POSITIVE", true, (int) id, "误报证据-" + id));
            decisions.put(sampleId, new GovernanceSemanticBacktestService.SemanticSampleResult(
                    false, RuleExecutionStatus.NOT_HIT, "模型明确判定未命中",
                    (int) id, null, "否定语境", 1));
        }
        when(sampleService.semanticSamples(group, 5)).thenReturn(samples);
        when(semantic.run(any(), any(), any())).thenReturn(
                new GovernanceSemanticBacktestService.SemanticBacktestOutcome(
                        decisions, 1, 50, 10, 0, List.of(), "FEEDBACK_GOVERNANCE_LLM_BACKTEST"));

        RuleBacktestResult result = new RuleBacktestService(
                groups, sampleService, sandbox, validation, properties, semantic)
                .run(6L, candidate, 100, 9L, 2);

        assertThat(result.executionStatus()).isEqualTo(BacktestExecutionStatus.COMPLETED);
        assertThat(result.sampleCount()).isEqualTo(5);
        assertThat(result.uniqueDocumentCount()).isEqualTo(1);
        assertThat(result.determinateSampleCount()).isEqualTo(5);
        assertThat(result.resolvedFalsePositiveCount()).isEqualTo(5);
        assertThat(result.riskLevel()).isEqualTo(BacktestRiskLevel.MEDIUM);
        assertThat(result.coverageWarnings()).contains(
                "CONFIRMED_POSITIVE_SAMPLE_MISSING",
                "NORMAL_SAMPLE_MISSING",
                "SAMPLES_SHARE_DOCUMENTS: samples=5, uniqueDocuments=1");
        assertThat(result.details()).extracting(detail -> detail.sampleId())
                .containsExactly("feedback:1", "feedback:2", "feedback:3", "feedback:4", "feedback:5");
    }

    @Test
    void requestBudgetSplitsBatchesAndInvalidEvidenceBecomesIndeterminate() {
        Fixture fixture = fixture(1_000, 900, true, 2);

        var outcome = fixture.service().run(candidate(), fixture.samples(),
                new GovernanceSemanticBacktestService.BacktestCallScope(11L, 22L, 3));

        assertThat(outcome.llmCallCount()).isEqualTo(2);
        assertThat(outcome.inputTokens()).isEqualTo(200);
        assertThat(outcome.results().values())
                .allSatisfy(result -> {
                    assertThat(result.matched()).isNull();
                    assertThat(result.detail()).contains("EVIDENCE_NOT_VERIFIED");
                });
    }

    @Test
    void propertiesEnableSemanticBacktestByDefaultAndFingerprintChangesWithBudget() {
        FeedbackGovernanceProperties properties = new FeedbackGovernanceProperties();
        assertThat(properties.getBacktest().isLlmEnabled()).isTrue();
        RuleBacktestService service = new RuleBacktestService(mock(com.example.disclosurereview.governance.persistence.repository.RuleFeedbackGovernanceGroupJpaRepository.class),
                mock(RuleBacktestSampleService.class), mock(RuleExecutionSandbox.class),
                mock(RuleCandidateValidationService.class), properties, null);
        String before = service.cacheFingerprint();
        properties.getBacktest().setSampleWindowChars(3_000);
        assertThat(service.cacheFingerprint()).isNotEqualTo(before);
    }

    @Test
    void proposalGateAcceptsPartialWithThreeDeterminateSamplesAndRejectsUnavailable() {
        FeedbackGovernanceProperties properties = new FeedbackGovernanceProperties();
        RuleBacktestService service = new RuleBacktestService(mock(com.example.disclosurereview.governance.persistence.repository.RuleFeedbackGovernanceGroupJpaRepository.class),
                mock(RuleBacktestSampleService.class), mock(RuleExecutionSandbox.class),
                mock(RuleCandidateValidationService.class), properties, null);
        RuleBacktestResult partial = result(BacktestExecutionStatus.PARTIAL, 3, 1);
        RuleBacktestResult unavailable = result(BacktestExecutionStatus.UNAVAILABLE, 2, 0);

        assertThatCode(() -> service.requireUsableForProposal(partial, candidate())).doesNotThrowAnyException();
        assertThatThrownBy(() -> service.requireUsableForProposal(unavailable, candidate()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("回测不可用");
    }

    private RuleBacktestResult result(BacktestExecutionStatus status, int determinate, int calls) {
        return new RuleBacktestResult("hash", 3, 3, 0, 0, 3, 0, 3, 0, 0, 0,
                3 - determinate, BacktestRiskLevel.HIGH, status, "LLM_POLICY", determinate, calls,
                100, 10, 0, 3, List.of(), List.of());
    }

    private Fixture fixture(int requestChars, int windowChars, boolean violated, int sampleCount) {
        ReviewTaskJpaRepository tasks = mock(ReviewTaskJpaRepository.class);
        DocumentPageJpaRepository pages = mock(DocumentPageJpaRepository.class);
        DocumentTypeAliasResolver aliases = mock(DocumentTypeAliasResolver.class);
        RuleReviewService ruleReviewService = mock(RuleReviewService.class);
        LlmGateway gateway = mock(LlmGateway.class);
        FeedbackGovernanceProperties properties = new FeedbackGovernanceProperties();
        properties.getBacktest().setMaximumRequestChars(requestChars);
        properties.getBacktest().setSampleWindowChars(windowChars);
        properties.getBacktest().setWindowOverlapChars(50);

        List<RuleBacktestSampleService.BacktestSample> samples = new ArrayList<>();
        StringBuilder response = new StringBuilder("{\"results\":[");
        for (long id = 1; id <= sampleCount; id++) {
            ReviewTaskEntity task = task(id);
            when(tasks.findById(id)).thenReturn(Optional.of(task));
            String pageText = requestChars <= 1_000 ? "长文档".repeat(180) + "正文存在真实证据"
                    : violated ? "正文存在真实证据" : "普通合规正文";
            when(pages.findByTaskIdOrderByPageNumber(id)).thenReturn(List.of(page(task,
                    pageText)));
            samples.add(new RuleBacktestSampleService.BacktestSample(id,
                    id <= 3 ? "FALSE_POSITIVE" : id == 4 ? "CONFIRMED_POSITIVE" : "NORMAL", id != 5));
            if (id > 1) response.append(',');
            response.append("{\"taskId\":").append(id)
                    .append(",\"segmentId\":\"task:").append(id).append("-1\",\"violated\":")
                    .append(violated)
                    .append(",\"confidence\":0.95,\"pageNumber\":1,\"evidenceText\":\"不存在的证据\",\"explanation\":\"判断\"}");
        }
        response.append("]}");
        String rawResponse = response.toString();
        when(gateway.chatCompletion(any(LlmCallContext.class), anyString(), anyString(), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Function<String, Object> handler = invocation.getArgument(3);
            Object parsed = handler.apply(rawResponse);
            return new LlmGatewayResponse<>(parsed, null, "provider", "model",
                    new LlmUsage(100, 20, 7, "{}"));
        });

        GovernanceSemanticBacktestService service = new GovernanceSemanticBacktestService(tasks, pages, aliases,
                ruleReviewService, new EvidenceVerifier(), gateway, mapper, properties);
        return new Fixture(service, gateway, List.copyOf(samples));
    }

    private RuleCandidate candidate() {
        var condition = mapper.createObjectNode().put("minConfidence", 0.75);
        var prompt = mapper.createObjectNode().put("reviewGoal", "识别违规承诺").put("criteria", "仅明确承诺命中");
        return new RuleCandidate("LLM_BACKTEST_RULE", "语义回测规则", RuleExecutorType.LLM_POLICY,
                mapper.createObjectNode(), condition, mapper.createObjectNode(), prompt, 100, true);
    }

    private ReviewTaskEntity task(long id) {
        ReviewTaskEntity task = new ReviewTaskEntity();
        ReflectionTestUtils.setField(task, "id", id);
        task.setOriginalFileName("sample-" + id + ".pdf");
        task.setDocumentCategory(DocumentCategory.PROTOCOL);
        task.setDeclaredDocumentType("投资协议书");
        return task;
    }

    private DocumentPageEntity page(ReviewTaskEntity task, String text) {
        DocumentPageEntity page = new DocumentPageEntity();
        page.setTask(task);
        page.setPageNumber(1);
        page.setRawText(text);
        page.setNormalizedText(text);
        page.setCharCount(text.length());
        return page;
    }

    private record Fixture(GovernanceSemanticBacktestService service,
                           LlmGateway gateway,
                           List<RuleBacktestSampleService.BacktestSample> samples) {}
}
