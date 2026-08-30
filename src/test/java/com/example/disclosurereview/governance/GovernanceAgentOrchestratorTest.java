package com.example.disclosurereview.governance;

import com.example.disclosurereview.config.FeedbackGovernanceProperties;
import com.example.disclosurereview.governance.agent.*;
import com.example.disclosurereview.governance.service.RuleProposalService;
import com.example.disclosurereview.governance.tool.*;
import com.example.disclosurereview.llm.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GovernanceAgentOrchestratorTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private GovernanceAgentToolRegistry registry;
    private GovernanceAgentStateService state;
    private RuleProposalService proposals;
    private LlmGateway gateway;
    private FeedbackGovernanceProperties properties;
    private GovernanceAgentOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        registry = mock(GovernanceAgentToolRegistry.class);
        state = mock(GovernanceAgentStateService.class);
        proposals = mock(RuleProposalService.class);
        gateway = mock(LlmGateway.class);
        properties = new FeedbackGovernanceProperties();
        properties.getAgent().setMaxToolIterations(4);
        when(registry.registered(anyString())).thenReturn(true);
        when(registry.execute(eq("getGovernanceAnalysisBrief"), any(), any()))
                .thenReturn(ToolExecutionResult.read(mapper.createObjectNode().put("brief", true)));
        GovernanceAgentPromptBuilder prompts = mock(GovernanceAgentPromptBuilder.class);
        when(prompts.systemPrompt()).thenReturn("system");
        when(prompts.structuredUserPrompt(anyLong(), anyLong(), anyList())).thenReturn("user");
        orchestrator = new GovernanceAgentOrchestrator(prompts, new GovernanceAgentResponseParser(mapper),
                registry, state, proposals, gateway, properties, mapper);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void invalidResponseAndIllegalToolCannotBypassProposalTool() {
        doReturn(response("not-json"),
                response("{\"nextAction\":\"CALL_TOOL\",\"toolName\":\"deleteAllRules\",\"arguments\":{}}"),
                response("{\"nextAction\":\"CALL_TOOL\",\"toolName\":\"proposeOptimizationAdvice\",\"arguments\":{}}"))
                .when(gateway).chatCompletion(any(LlmCallContext.class), anyString(), anyString(), any(Function.class));
        when(registry.execute(eq("deleteAllRules"), any(), any()))
                .thenThrow(new SecurityException("未注册的治理 Tool"));
        when(registry.execute(eq("proposeOptimizationAdvice"), any(), any()))
                .thenReturn(ToolExecutionResult.proposal(mapper.createObjectNode().put("proposalId", 42), 42L));

        GovernanceAgentOrchestrator.AnalysisResult result = orchestrator.analyze(1L, 2L);

        assertThat(result.proposalId()).isEqualTo(42L);
        assertThat(result.toolIterations()).isEqualTo(3);
        verify(proposals).attachAgentCallById(eq(42L), isNull(), eq("provider"), eq("model"), anyString());
        verify(state, never()).fail(anyLong(), anyString());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void finishWithoutProposalDefersWithoutTreatingItAsRetryableFailure() {
        properties.getAgent().setMaxToolIterations(2);
        doReturn(response("{\"nextAction\":\"FINISH\"}"))
                .when(gateway).chatCompletion(any(LlmCallContext.class), anyString(), anyString(), any(Function.class));

        GovernanceAgentOrchestrator.AnalysisResult result = orchestrator.analyze(1L, 2L);

        assertThat(result.proposalId()).isNull();
        verify(state).defer(eq(2L), contains("未形成可安全提交的提案"));
        verify(state, never()).fail(anyLong(), anyString());
        verifyNoInteractions(proposals);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void boundedAnalysisBriefLeavesEnoughIterationsForRegexArtifactsAndProposal() {
        properties.getAgent().setMaxToolIterations(8);
        String candidate = "{\"ruleCode\":\"TEST_RULE\",\"executorType\":\"REGEX\"}";
        doReturn(
                response("{\"nextAction\":\"CALL_TOOL\",\"toolName\":\"getGovernanceAnalysisBrief\",\"arguments\":{\"groupId\":2}}"),
                response("{\"nextAction\":\"CALL_TOOL\",\"toolName\":\"validateRuleConfig\",\"arguments\":{\"candidateRule\":" + candidate + "}}"),
                response("{\"nextAction\":\"CALL_TOOL\",\"toolName\":\"compileRegex\",\"arguments\":{\"patterns\":[\"TEST\"],\"candidateHash\":\"h\"}}"),
                response("{\"nextAction\":\"CALL_TOOL\",\"toolName\":\"compareRuleVersions\",\"arguments\":{\"sourceRuleVersionId\":14,\"candidateRule\":" + candidate + "}}"),
                response("{\"nextAction\":\"CALL_TOOL\",\"toolName\":\"checkRuleConflict\",\"arguments\":{\"candidateRule\":" + candidate + "}}"),
                response("{\"nextAction\":\"CALL_TOOL\",\"toolName\":\"runRuleBacktest\",\"arguments\":{\"feedbackGroupId\":2,\"candidateRule\":" + candidate + "}}"),
                response("{\"nextAction\":\"CALL_TOOL\",\"toolName\":\"estimateAffectedDocuments\",\"arguments\":{\"feedbackGroupId\":2,\"candidateRule\":" + candidate + "}}"),
                response("{\"nextAction\":\"CALL_TOOL\",\"toolName\":\"proposeRuleUpdate\",\"arguments\":{\"governanceGroupId\":2}}"))
                .when(gateway).chatCompletion(any(LlmCallContext.class), anyString(), anyString(), any(Function.class));
        when(registry.execute(anyString(), any(), any())).thenAnswer(invocation ->
                "proposeRuleUpdate".equals(invocation.getArgument(0))
                        ? ToolExecutionResult.proposal(mapper.createObjectNode().put("proposalId", 88), 88L)
                        : ToolExecutionResult.read(mapper.createObjectNode().put("ok", true)));

        GovernanceAgentOrchestrator.AnalysisResult result = orchestrator.analyze(1L, 2L);

        assertThat(result.proposalId()).isEqualTo(88L);
        assertThat(result.toolIterations()).isEqualTo(9);
        verify(gateway, times(8)).chatCompletion(any(LlmCallContext.class), anyString(), anyString(), any(Function.class));
        verify(state, never()).defer(anyLong(), anyString());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void structuredModeExecutesMultipleIndependentToolsInOneModelRound() {
        properties.getAgent().setMaxModelIterations(3);
        properties.getAgent().setMaxToolsPerRound(4);
        doReturn(
                response("{\"nextAction\":\"CALL_TOOLS\",\"toolCalls\":["
                        + "{\"callId\":\"a\",\"toolName\":\"getFeedbackSamples\",\"arguments\":{\"groupId\":2}},"
                        + "{\"callId\":\"b\",\"toolName\":\"getHistoricalGovernanceDecisions\",\"arguments\":{}}]}"),
                response("{\"nextAction\":\"CALL_TOOLS\",\"toolCalls\":[{\"callId\":\"p\",\"toolName\":\"proposeNoAction\",\"arguments\":{}}]}")
        ).when(gateway).chatCompletion(any(LlmCallContext.class), anyString(), anyString(), any(Function.class));
        when(registry.parallelSafe(anyString())).thenReturn(true);
        when(registry.execute(anyString(), any(), any())).thenAnswer(invocation ->
                "proposeNoAction".equals(invocation.getArgument(0))
                        ? ToolExecutionResult.proposal(mapper.createObjectNode().put("proposalId", 66), 66L)
                        : ToolExecutionResult.read(mapper.createObjectNode().put("ok", true)));

        GovernanceAgentOrchestrator.AnalysisResult result = orchestrator.analyze(1L, 2L);

        assertThat(result.proposalId()).isEqualTo(66L);
        assertThat(result.toolIterations()).isEqualTo(4);
        verify(gateway, times(2)).chatCompletion(any(LlmCallContext.class), anyString(), anyString(), any(Function.class));
        verify(registry).execute(eq("getFeedbackSamples"), any(), argThat(context -> context.iterationNumber() == 1));
        verify(registry).execute(eq("getHistoricalGovernanceDecisions"), any(), argThat(context -> context.iterationNumber() == 1));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void oversizedStructuredBatchExecutesAllowedPrefixAndDefersRemainder() {
        properties.getAgent().setMaxModelIterations(3);
        properties.getAgent().setMaxToolsPerRound(2);
        doReturn(
                response("{\"nextAction\":\"CALL_TOOLS\",\"toolCalls\":["
                        + "{\"callId\":\"a\",\"toolName\":\"getFeedbackSamples\",\"arguments\":{\"groupId\":2}},"
                        + "{\"callId\":\"b\",\"toolName\":\"getHistoricalGovernanceDecisions\",\"arguments\":{}},"
                        + "{\"callId\":\"c\",\"toolName\":\"getSimilarAcceptedProposals\",\"arguments\":{}}]}"),
                response("{\"nextAction\":\"CALL_TOOLS\",\"toolCalls\":["
                        + "{\"callId\":\"p\",\"toolName\":\"proposeNoAction\",\"arguments\":{}}]}")
        ).when(gateway).chatCompletion(any(LlmCallContext.class), anyString(), anyString(), any(Function.class));
        when(registry.parallelSafe(anyString())).thenReturn(true);
        when(registry.execute(anyString(), any(), any())).thenAnswer(invocation ->
                "proposeNoAction".equals(invocation.getArgument(0))
                        ? ToolExecutionResult.proposal(mapper.createObjectNode().put("proposalId", 77), 77L)
                        : ToolExecutionResult.read(mapper.createObjectNode().put("ok", true)));

        GovernanceAgentOrchestrator.AnalysisResult result = orchestrator.analyze(1L, 2L);

        assertThat(result.proposalId()).isEqualTo(77L);
        assertThat(result.toolIterations()).isEqualTo(4);
        verify(registry).execute(eq("getFeedbackSamples"), any(), any());
        verify(registry).execute(eq("getHistoricalGovernanceDecisions"), any(), any());
        verify(registry, never()).execute(eq("getSimilarAcceptedProposals"), any(), any());
        verify(registry).execute(eq("proposeNoAction"), any(), any());
        verify(state, never()).defer(anyLong(), anyString());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void finalProposalReturnedAfterDeadlineStillExecutes() {
        properties.getAgent().setTimeoutSeconds(1);
        properties.getAgent().setMaxModelIterations(2);
        when(gateway.chatCompletion(any(LlmCallContext.class), anyString(), anyString(), any(Function.class)))
                .thenAnswer(invocation -> {
                    Thread.sleep(1_100);
                    return response("{\"nextAction\":\"CALL_TOOLS\",\"toolCalls\":["
                            + "{\"callId\":\"p\",\"toolName\":\"proposeNoAction\",\"arguments\":{}}]}");
                });
        when(registry.execute(eq("proposeNoAction"), any(), any()))
                .thenReturn(ToolExecutionResult.proposal(mapper.createObjectNode().put("proposalId", 91), 91L));

        GovernanceAgentOrchestrator.AnalysisResult result = orchestrator.analyze(1L, 2L);

        assertThat(result.proposalId()).isEqualTo(91L);
        verify(registry).execute(eq("proposeNoAction"), any(), any());
        verify(state, never()).defer(anyLong(), anyString());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void nonProposalBatchReturnedAfterDeadlineEndsGraphWithoutExecutionException() {
        properties.getAgent().setTimeoutSeconds(1);
        properties.getAgent().setMaxModelIterations(2);
        when(gateway.chatCompletion(any(LlmCallContext.class), anyString(), anyString(), any(Function.class)))
                .thenAnswer(invocation -> {
                    Thread.sleep(1_100);
                    return response("{\"nextAction\":\"CALL_TOOLS\",\"toolCalls\":["
                            + "{\"callId\":\"v\",\"toolName\":\"validateRuleConfig\",\"arguments\":{}}]}");
                });

        GovernanceAgentOrchestrator.AnalysisResult result = orchestrator.analyze(1L, 2L);

        assertThat(result.proposalId()).isNull();
        verify(registry, never()).execute(eq("validateRuleConfig"), any(), any());
        verify(state).defer(eq(2L), contains("已正常结束状态机"));
        verify(state, never()).fail(anyLong(), anyString());
    }

    private LlmGatewayResponse<String> response(String content) {
        return new LlmGatewayResponse<>(content, null, "provider", "model", LlmUsage.empty());
    }
}
