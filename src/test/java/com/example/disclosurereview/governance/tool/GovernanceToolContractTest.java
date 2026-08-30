package com.example.disclosurereview.governance.tool;

import com.example.disclosurereview.governance.service.*;
import com.example.disclosurereview.llm.EvidenceVerifier;
import com.example.disclosurereview.llm.LlmGateway;
import com.example.disclosurereview.persistence.entity.ReviewRuleVersionEntity;
import com.example.disclosurereview.rule.executor.LlmPolicyRuleExecutor;
import com.example.disclosurereview.rule.executor.RuleJsonSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GovernanceToolContractTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void candidateToolSchemaExposesRootPromptContractRecursively() {
        GovernanceAgentTool validate = catalog().tools().stream()
                .filter(tool -> tool.getName().equals("validateRuleConfig"))
                .findFirst().orElseThrow();
        JsonNode candidate = validate.getInputSchema().path("properties").path("candidateRule");

        assertThat(candidate.path("required")).extracting(JsonNode::asText)
                .contains("condition", "action", "prompt");
        assertThat(candidate.path("description").asText()).contains("根级", "禁止使用 promptJson");
        assertThat(candidate.path("properties").path("prompt").path("properties").has("reviewGoal")).isTrue();
        assertThat(candidate.path("properties").path("condition").path("description").asText())
                .contains("不要在此放 prompt");

        ObjectNode malformed = GovernanceRuleCandidateContract.llmPolicyExample(mapper);
        JsonNode prompt = malformed.remove("prompt");
        ((ObjectNode) malformed.path("condition")).set("prompt", prompt);
        ObjectNode arguments = mapper.createObjectNode().set("candidateRule", malformed);
        assertThat(new GovernanceToolSchemaValidator().validate(validate.getInputSchema(), arguments))
                .contains("candidateRule.prompt is required");
    }

    @Test
    void normalizerRepairsEveryPromptShapeSeenInLatestGovernanceRun() throws Exception {
        for (String misplacedPrompt : new String[]{
                "{\"reviewGoal\":\"识别正向保本承诺\",\"criteria\":[\"正向承诺违规\",\"否定语境不违规\"]}",
                "{\"promptJson\":{\"reviewGoal\":\"识别正向保本承诺\",\"criteria\":[\"正向承诺违规\",\"否定语境不违规\"]}}"
        }) {
            ObjectNode candidate = GovernanceRuleCandidateContract.llmPolicyExample(mapper);
            candidate.remove("prompt");
            ((ObjectNode) candidate.path("condition")).set("prompt", mapper.readTree(misplacedPrompt));
            ObjectNode arguments = mapper.createObjectNode().set("candidateRule", candidate);

            GovernanceToolArgumentNormalizer.NormalizationResult result =
                    new GovernanceToolArgumentNormalizer(mapper).normalize(arguments);

            assertThat(result.arguments().path("candidateRule").path("prompt").path("reviewGoal").asText())
                    .isEqualTo("识别正向保本承诺");
            assertThat(result.arguments().path("candidateRule").path("prompt").path("criteria").isTextual()).isTrue();
            assertThat(result.arguments().path("candidateRule").path("condition").has("prompt")).isFalse();
            assertThat(result.repairs()).anyMatch(value -> value.contains("prompt 已规范化为根级字段"));
        }
    }

    @Test
    void llmExecutorValidationUsesCandidateFieldNameInsteadOfPersistenceFieldName() {
        ReviewRuleVersionEntity version = new ReviewRuleVersionEntity();
        version.setPromptJson("{}");
        var result = new LlmPolicyRuleExecutor(mock(LlmGateway.class), mock(EvidenceVerifier.class),
                new RuleJsonSupport(mapper)).validate(version);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).containsExactly("candidateRule.prompt.reviewGoal is required");
    }

    @Test
    void backtestToolCacheIncludesServerExecutionFingerprint() {
        RuleBacktestService backtest = mock(RuleBacktestService.class);
        when(backtest.cacheFingerprint()).thenReturn("semantic-backtest-v2|llm=true|window=6000");
        GovernanceAgentTool tool = new GovernanceAgentToolCatalog(
                mock(GovernanceToolDataService.class), mock(FeedbackGovernanceGroupService.class),
                mock(RuleCandidateValidationService.class), backtest,
                mock(RuleProposalService.class), new GovernanceJsonService(mapper), mapper).tools().stream()
                .filter(candidate -> candidate.getName().equals("runRuleBacktest")).findFirst().orElseThrow();

        String first = tool.cacheDiscriminator(mapper.createObjectNode(),
                new GovernanceToolExecutionContext(1L, 2L, 3, "tester"));
        when(backtest.cacheFingerprint()).thenReturn("semantic-backtest-v3|llm=true|window=3000");
        String second = tool.cacheDiscriminator(mapper.createObjectNode(),
                new GovernanceToolExecutionContext(1L, 2L, 3, "tester"));

        assertThat(first).contains("semantic-backtest-v2", "llm=true");
        assertThat(second).isNotEqualTo(first);
    }

    private GovernanceAgentToolCatalog catalog() {
        return new GovernanceAgentToolCatalog(
                mock(GovernanceToolDataService.class), mock(FeedbackGovernanceGroupService.class),
                mock(RuleCandidateValidationService.class), mock(RuleBacktestService.class),
                mock(RuleProposalService.class), new GovernanceJsonService(mapper), mapper);
    }
}
