package com.example.disclosurereview.governance;

import com.example.disclosurereview.config.FeedbackGovernanceProperties;
import com.example.disclosurereview.governance.agent.GovernanceAgentResponseParser;
import com.example.disclosurereview.governance.agent.GovernanceAgentPromptBuilder;
import com.example.disclosurereview.governance.domain.*;
import com.example.disclosurereview.governance.persistence.entity.RuleFeedbackGovernanceGroupEntity;
import com.example.disclosurereview.governance.persistence.entity.RuleGovernanceRunEntity;
import com.example.disclosurereview.governance.persistence.repository.*;
import com.example.disclosurereview.governance.service.*;
import com.example.disclosurereview.governance.tool.GovernanceAgentToolCatalog;
import com.example.disclosurereview.governance.tool.GovernanceAgentToolRegistry;
import com.example.disclosurereview.governance.tool.GovernanceAgentTool;
import com.example.disclosurereview.governance.tool.GovernanceToolSchemaValidator;
import com.example.disclosurereview.persistence.entity.ReviewRuleFeedbackEntity;
import com.example.disclosurereview.persistence.entity.ReviewRuleDefinitionEntity;
import com.example.disclosurereview.persistence.entity.ReviewRuleVersionEntity;
import com.example.disclosurereview.persistence.entity.ReviewTaskEntity;
import com.example.disclosurereview.persistence.repository.DocumentPageJpaRepository;
import com.example.disclosurereview.persistence.repository.ReviewRuleDefinitionJpaRepository;
import com.example.disclosurereview.persistence.repository.ReviewRuleFeedbackJpaRepository;
import com.example.disclosurereview.persistence.repository.ReviewRuleVersionJpaRepository;
import com.example.disclosurereview.persistence.repository.ReviewTaskJpaRepository;
import com.example.disclosurereview.rule.domain.RuleExecutionStatus;
import com.example.disclosurereview.rule.domain.RuleExecutorType;
import com.example.disclosurereview.rule.executor.RuleExecutorRegistry;
import com.example.disclosurereview.model.DocumentCategory;
import com.example.disclosurereview.model.DocumentType;
import com.example.disclosurereview.strategy.DocumentTypeAliasResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.time.Instant;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FeedbackGovernanceDomainTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void groupKeyIsStableAndSeparatesRuleVersionAndScope() {
        ReviewRuleFeedbackEntity feedback = new ReviewRuleFeedbackEntity();
        feedback.setRuleCode(" rule_a ");
        feedback.setRuleVersionId(7L);
        feedback.setDocumentCategory("protocol");
        feedback.setDeclaredDocumentType("投资|协议书");
        feedback.setFeedbackType("false_positive");

        assertThat(new GovernanceGroupKeyService().key(feedback))
                .isEqualTo("RULE_A|7|PROTOCOL|投资_协议书|FALSE_POSITIVE");
    }

    @Test
    void aggregationCreatesOneGroupOnlyAfterThresholdAndClaimsFeedback() {
        FeedbackGovernanceProperties properties = new FeedbackGovernanceProperties();
        properties.setMinimumFeedbackCount(3);
        ReviewRuleFeedbackJpaRepository feedbacks = mock(ReviewRuleFeedbackJpaRepository.class);
        ReviewRuleVersionJpaRepository versions = mock(ReviewRuleVersionJpaRepository.class);
        RuleFeedbackGovernanceGroupJpaRepository groups = mock(RuleFeedbackGovernanceGroupJpaRepository.class);
        RuleFeedbackGovernanceGroupItemJpaRepository items = mock(RuleFeedbackGovernanceGroupItemJpaRepository.class);
        ReviewRuleDefinitionEntity definition = new ReviewRuleDefinitionEntity();
        definition.setRuleCode("RULE_A");
        ReviewRuleVersionEntity version = new ReviewRuleVersionEntity();
        version.setRuleDefinition(definition); version.setVersionCode("v1");
        List<ReviewRuleFeedbackEntity> rows = List.of(feedback(1L), feedback(2L), feedback(3L));
        when(feedbacks.findByProcessStatusInAndCreatedAtAfterOrderByCreatedAtAsc(anyCollection(), any()))
                .thenReturn(rows);
        when(items.existsByFeedback_Id(nullable(Long.class))).thenReturn(false);
        when(versions.findById(11L)).thenReturn(Optional.of(version));
        when(groups.findFirstByGroupKeyAndStatusInOrderByCreatedAtDesc(anyString(), anyCollection())).thenReturn(Optional.empty());
        when(groups.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        FeedbackGovernanceGroupService service = new FeedbackGovernanceGroupService(properties, feedbacks, versions,
                groups, items, new GovernanceGroupKeyService());

        var result = service.createGroups(new RuleGovernanceRunEntity());

        assertThat(result.scannedFeedbackCount()).isEqualTo(3);
        assertThat(result.skippedFeedbackCount()).isZero();
        assertThat(result.groups()).singleElement().satisfies(group -> {
            assertThat(group.getFeedbackCount()).isEqualTo(3);
            assertThat(group.getStatus()).isEqualTo(GovernanceGroupStatus.PENDING);
        });
        assertThat(rows).allSatisfy(row -> assertThat(row.getProcessStatus()).isEqualTo("GROUPED"));
        verify(items, times(3)).save(any());
    }

    @Test
    void aggregationExplainsAndSkipsFeedbackWhenRuleCodeDoesNotMatchItsVersion() {
        FeedbackGovernanceProperties properties = new FeedbackGovernanceProperties();
        properties.setMinimumFeedbackCount(3);
        ReviewRuleFeedbackJpaRepository feedbacks = mock(ReviewRuleFeedbackJpaRepository.class);
        ReviewRuleVersionJpaRepository versions = mock(ReviewRuleVersionJpaRepository.class);
        RuleFeedbackGovernanceGroupJpaRepository groups = mock(RuleFeedbackGovernanceGroupJpaRepository.class);
        RuleFeedbackGovernanceGroupItemJpaRepository items = mock(RuleFeedbackGovernanceGroupItemJpaRepository.class);
        ReviewRuleDefinitionEntity definition = new ReviewRuleDefinitionEntity();
        definition.setRuleCode("RULE_B");
        ReviewRuleVersionEntity version = new ReviewRuleVersionEntity();
        version.setRuleDefinition(definition); version.setVersionCode("v1");
        List<ReviewRuleFeedbackEntity> rows = List.of(feedback(1L), feedback(2L), feedback(3L));
        when(feedbacks.findByProcessStatusInAndCreatedAtAfterOrderByCreatedAtAsc(anyCollection(), any()))
                .thenReturn(rows);
        when(items.existsByFeedback_Id(nullable(Long.class))).thenReturn(false);
        when(versions.findById(11L)).thenReturn(Optional.of(version));
        FeedbackGovernanceGroupService service = new FeedbackGovernanceGroupService(properties, feedbacks, versions,
                groups, items, new GovernanceGroupKeyService());

        var result = service.createGroups(new RuleGovernanceRunEntity());

        assertThat(result.groups()).isEmpty();
        assertThat(result.skippedFeedbackCount()).isEqualTo(3);
        assertThat(result.skippedReasons()).containsEntry("RULE_CODE_VERSION_MISMATCH", 3);
        verify(groups, never()).save(any());
    }

    @Test
    void aggregationCreatesRuleGapFromSingleFalseNegativeWithoutSourceRule() {
        FeedbackGovernanceProperties properties = new FeedbackGovernanceProperties();
        assertThat(properties.getMinimumFeedbackCount()).isEqualTo(1);
        ReviewRuleFeedbackJpaRepository feedbacks = mock(ReviewRuleFeedbackJpaRepository.class);
        ReviewRuleVersionJpaRepository versions = mock(ReviewRuleVersionJpaRepository.class);
        RuleFeedbackGovernanceGroupJpaRepository groups = mock(RuleFeedbackGovernanceGroupJpaRepository.class);
        RuleFeedbackGovernanceGroupItemJpaRepository items = mock(RuleFeedbackGovernanceGroupItemJpaRepository.class);
        ReviewRuleFeedbackEntity feedback = new ReviewRuleFeedbackEntity();
        com.example.disclosurereview.persistence.entity.ReviewIssueEntity issue =
                new com.example.disclosurereview.persistence.entity.ReviewIssueEntity();
        issue.setIssueCode("CONTENT_LOGIC_CONFLICT");
        feedback.setIssue(issue);
        feedback.setRuleCode("CONTENT_LOGIC_CONFLICT");
        feedback.setFeedbackType("FALSE_NEGATIVE");
        feedback.setProcessStatus("PENDING");
        feedback.setDocumentCategory("PROTOCOL");
        feedback.setDeclaredDocumentType("产品说明书");
        feedback.setCreatedAt(Instant.now());
        when(feedbacks.findByProcessStatusInAndCreatedAtAfterOrderByCreatedAtAsc(anyCollection(), any()))
                .thenReturn(List.of(feedback));
        when(items.existsByFeedback_Id(nullable(Long.class))).thenReturn(false);
        when(groups.findFirstByGroupKeyAndStatusInOrderByCreatedAtDesc(anyString(), anyCollection()))
                .thenReturn(Optional.empty());
        when(groups.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        FeedbackGovernanceGroupService service = new FeedbackGovernanceGroupService(properties, feedbacks, versions,
                groups, items, new GovernanceGroupKeyService());

        var result = service.createGroups(new RuleGovernanceRunEntity());

        assertThat(result.scannedFeedbackCount()).isEqualTo(1);
        assertThat(result.skippedFeedbackCount()).isZero();
        assertThat(result.groups()).singleElement().satisfies(group -> {
            assertThat(group.getGovernanceIntent()).isEqualTo(GovernanceIntent.RULE_GAP);
            assertThat(group.getFeedbackType()).isEqualTo("FALSE_NEGATIVE");
            assertThat(group.getIssueType()).isEqualTo("CONTENT_LOGIC_CONFLICT");
            assertThat(group.getRuleCode()).isNull();
            assertThat(group.getRuleVersionEntity()).isNull();
            assertThat(group.getFeedbackCount()).isEqualTo(1);
        });
        assertThat(feedback.getProcessStatus()).isEqualTo("GROUPED");
        verifyNoInteractions(versions);
        verify(items).save(any());
    }

    @Test
    void actionPolicyRejectsUnsafeRootCauseAndProposalCombination() {
        GovernanceActionPolicy policy = new GovernanceActionPolicy();
        assertThat(policy.allowed(RootCauseType.DOCUMENT_PARSING, ProposalType.OPTIMIZATION_ADVICE)).isTrue();
        assertThat(policy.allowed(RootCauseType.DOCUMENT_PARSING, ProposalType.UPDATE_RULE)).isFalse();
        assertThat(policy.allowed(RootCauseType.RULE_EXECUTOR, ProposalType.COMPOSITE_RULE_CHANGE)).isTrue();
        assertThat(policy.allowed(RootCauseType.LLM_POLICY, ProposalType.COMPOSITE_RULE_CHANGE)).isTrue();
        assertThatThrownBy(() -> policy.requireAllowed(RootCauseType.INSUFFICIENT_EVIDENCE, ProposalType.DISABLE_RULE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void structuredPromptAndAdviceSchemaExposeRepairableToolContract() throws Exception {
        GovernanceAgentToolRegistry registry = mock(GovernanceAgentToolRegistry.class);
        when(registry.definitions()).thenReturn(List.of(new GovernanceAgentToolRegistry.ToolDefinition(
                "proposeCompositeRuleChange", "复合提案", mapper.readTree("{\"type\":\"object\"}"))));
        String prompt = new GovernanceAgentPromptBuilder(registry, mapper)
                .structuredUserPrompt(1L, 2L, List.of());
        assertThat(prompt).contains("governanceGroupId=2", "feedbackGroupId=2",
                "proposeCompositeRuleChange", "DISABLE_RULE", "CREATE_RULE", "禁止使用旧字段 groupId/rootCause/advice",
                "candidateRule.prompt.reviewGoal", "不要使用持久化字段名 promptJson",
                "\"prompt\":{\"reviewGoal\"");

        GovernanceAgentToolCatalog catalog = new GovernanceAgentToolCatalog(
                mock(GovernanceToolDataService.class), mock(FeedbackGovernanceGroupService.class),
                mock(RuleCandidateValidationService.class), mock(RuleBacktestService.class),
                mock(RuleProposalService.class), new GovernanceJsonService(mapper), mapper);
        GovernanceAgentTool advice = catalog.tools().stream()
                .filter(tool -> tool.getName().equals("proposeOptimizationAdvice")).findFirst().orElseThrow();
        assertThat(new GovernanceToolSchemaValidator().validate(advice.getInputSchema(), mapper.readTree("""
                {"governanceGroupId":2,"rootCauseType":"LLM_POLICY","problemSummary":"p","rootCauseAnalysis":"r","agentConfidence":0.9}
                """))).contains("optimizationCategory is required", "optimizationAdvice is required");
    }

    @Test
    void structuredAgentResponseAndToolSchemaAreStrict() throws Exception {
        GovernanceAgentResponseParser parser = new GovernanceAgentResponseParser(mapper);
        var step = parser.parse("```json\n{\"nextAction\":\"CALL_TOOL\",\"toolName\":\"getFeedbackGroup\",\"arguments\":{\"groupId\":9}}\n```");
        assertThat(step.toolName()).isEqualTo("getFeedbackGroup");
        var batch = parser.parse("{\"nextAction\":\"CALL_TOOLS\",\"toolCalls\":["
                + "{\"callId\":\"a\",\"toolName\":\"getFeedbackGroup\",\"arguments\":{\"groupId\":9}},"
                + "{\"callId\":\"b\",\"toolName\":\"getFeedbackSamples\",\"arguments\":{\"groupId\":9}}]}");
        assertThat(batch.toolCalls()).hasSize(2);
        assertThat(batch.toolCalls()).extracting(GovernanceAgentResponseParser.AgentToolCall::callId)
                .containsExactly("a", "b");
        assertThat(step.arguments().path("groupId").asLong()).isEqualTo(9L);
        assertThatThrownBy(() -> parser.parse("{\"nextAction\":\"EXECUTE_SHELL\"}"))
                .isInstanceOf(IllegalArgumentException.class);

        var schema = mapper.readTree("""
                {"type":"object","required":["groupId"],"additionalProperties":false,
                 "properties":{"groupId":{"type":"integer"}}}
                """);
        GovernanceToolSchemaValidator validator = new GovernanceToolSchemaValidator();
        assertThat(validator.validate(schema, mapper.readTree("{\"groupId\":1}"))).isEmpty();
        assertThat(validator.validate(schema, mapper.readTree("{\"groupId\":\"1\",\"command\":\"rm\"}")))
                .contains("groupId type must be integer", "unknown field: command");
    }

    @Test
    void candidateValidationForbidsJavaPluginAndUsesRe2() {
        RuleExecutorRegistry registry = mock(RuleExecutorRegistry.class);
        ReviewRuleDefinitionJpaRepository definitions = mock(ReviewRuleDefinitionJpaRepository.class);
        ReviewRuleVersionJpaRepository versions = mock(ReviewRuleVersionJpaRepository.class);
        when(definitions.findByRuleCode(anyString())).thenReturn(Optional.empty());
        when(definitions.findAll()).thenReturn(List.of());
        RuleCandidateValidationService service = new RuleCandidateValidationService(registry, definitions, versions,
                new GovernanceJsonService(mapper), mapper);
        var object = JsonNodeFactory.instance.objectNode();
        RuleCandidate candidate = new RuleCandidate("AGENT_PLUGIN", "危险插件", RuleExecutorType.JAVA_PLUGIN,
                object, object, object, object, 100, true);

        CandidateValidationResult result = service.validate(candidate, null, true);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(value -> value.contains("JAVA_PLUGIN"));
        assertThat(service.compileRegex(List.of("产品代码[:：]\\s*[A-Z0-9]+", "(")))
                .extracting(RuleCandidateValidationService.RegexCompileResult::valid)
                .containsExactly(true, false);

        RuleCandidate hybridWithoutLocator = new RuleCandidate("AGENT_HYBRID", "混合规则",
                RuleExecutorType.HYBRID, object, object, object,
                mapper.createObjectNode().put("reviewGoal", "复核候选"), 100, true);
        assertThat(service.validate(hybridWithoutLocator, null, true).errors())
                .anyMatch(value -> value.contains("condition.locator"));
    }

    @Test
    void backtestRatesResolvedFalsePositivesLowAndRegressionHigh() {
        RuleFeedbackGovernanceGroupJpaRepository groups = mock(RuleFeedbackGovernanceGroupJpaRepository.class);
        RuleBacktestSampleService samples = mock(RuleBacktestSampleService.class);
        RuleExecutionSandbox sandbox = mock(RuleExecutionSandbox.class);
        RuleCandidateValidationService validation = mock(RuleCandidateValidationService.class);
        FeedbackGovernanceProperties properties = new FeedbackGovernanceProperties();
        properties.setMinimumFeedbackCount(3);
        RuleFeedbackGovernanceGroupEntity group = new RuleFeedbackGovernanceGroupEntity();
        group.setRuleCode("RULE_A");
        when(groups.findById(1L)).thenReturn(Optional.of(group));
        RuleCandidate candidate = new RuleCandidate("RULE_A", "规则A", RuleExecutorType.REGEX,
                mapper.createObjectNode(), mapper.createObjectNode(), mapper.createObjectNode(), mapper.createObjectNode(), 100, true);
        when(validation.validate(candidate, "RULE_A", false))
                .thenReturn(new CandidateValidationResult(true, "hash", List.of(), List.of(), List.of()));
        when(samples.samples(group, 100)).thenReturn(List.of(
                new RuleBacktestSampleService.BacktestSample(1L, "FALSE_POSITIVE", true),
                new RuleBacktestSampleService.BacktestSample(2L, "FALSE_POSITIVE", true),
                new RuleBacktestSampleService.BacktestSample(3L, "FALSE_POSITIVE", true)));
        when(sandbox.executeCandidate(eq(candidate), anyLong()))
                .thenReturn(new RuleExecutionSandbox.SandboxResult(false, RuleExecutionStatus.NOT_HIT, "resolved"));
        RuleBacktestService service = new RuleBacktestService(groups, samples, sandbox, validation, properties);

        assertThat(service.run(1L, candidate, 100).riskLevel()).isEqualTo(BacktestRiskLevel.LOW);

        when(samples.samples(group, 100)).thenReturn(List.of(
                new RuleBacktestSampleService.BacktestSample(1L, "FALSE_POSITIVE", true),
                new RuleBacktestSampleService.BacktestSample(2L, "CONFIRMED_POSITIVE", true),
                new RuleBacktestSampleService.BacktestSample(3L, "NORMAL", false)));
        when(sandbox.executeCandidate(candidate, 1L)).thenReturn(new RuleExecutionSandbox.SandboxResult(false, RuleExecutionStatus.NOT_HIT, "resolved"));
        when(sandbox.executeCandidate(candidate, 2L)).thenReturn(new RuleExecutionSandbox.SandboxResult(true, RuleExecutionStatus.HIT, "retained"));
        when(sandbox.executeCandidate(candidate, 3L)).thenReturn(new RuleExecutionSandbox.SandboxResult(true, RuleExecutionStatus.HIT, "regression"));
        assertThat(service.run(1L, candidate, 100).riskLevel()).isEqualTo(BacktestRiskLevel.HIGH);

        when(samples.samples(group, 100)).thenReturn(List.of(
                new RuleBacktestSampleService.BacktestSample(1L, "FALSE_POSITIVE", true)));
        assertThat(service.run(1L, candidate, 100).riskLevel()).isEqualTo(BacktestRiskLevel.HIGH);
    }

    @Test
    void llmCandidateBacktestIsIndeterminateWhenExplicitlyDisabled() {
        ReviewTaskJpaRepository tasks = mock(ReviewTaskJpaRepository.class);
        DocumentPageJpaRepository pages = mock(DocumentPageJpaRepository.class);
        DocumentTypeAliasResolver aliases = mock(DocumentTypeAliasResolver.class);
        RuleExecutorRegistry executors = mock(RuleExecutorRegistry.class);
        FeedbackGovernanceProperties properties = new FeedbackGovernanceProperties();
        properties.getBacktest().setLlmEnabled(false);
        ReviewTaskEntity task = new ReviewTaskEntity();
        task.setDocumentCategory(DocumentCategory.PROTOCOL);
        task.setDeclaredDocumentType("投资协议书");
        when(tasks.findById(8L)).thenReturn(Optional.of(task));
        when(aliases.resolve("投资协议书")).thenReturn(DocumentType.INVESTMENT_AGREEMENT);
        RuleCandidate candidate = new RuleCandidate("LLM_RULE", "语义规则", RuleExecutorType.LLM_POLICY,
                mapper.createObjectNode(), mapper.createObjectNode(), mapper.createObjectNode(), mapper.createObjectNode(), 100, true);

        var result = new RuleExecutionSandbox(tasks, pages, aliases, executors, properties, mapper)
                .executeCandidate(candidate, 8L);

        assertThat(result.matched()).isNull();
        assertThat(result.status()).isEqualTo(RuleExecutionStatus.INDETERMINATE);
        assertThat(result.detail()).isEqualTo("LLM_RULE_BACKTEST_DISABLED");
        verifyNoInteractions(executors);
    }

    private ReviewRuleFeedbackEntity feedback(Long id) {
        ReviewRuleFeedbackEntity row = new ReviewRuleFeedbackEntity();
        row.setRuleCode("RULE_A");
        row.setRuleVersionId(11L);
        row.setDocumentCategory("PROTOCOL");
        row.setDeclaredDocumentType("投资协议书");
        row.setFeedbackType("FALSE_POSITIVE");
        row.setCreatedAt(Instant.now());
        return row;
    }
}
