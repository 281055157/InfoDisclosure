package com.example.disclosurereview.pipeline;

import com.example.disclosurereview.llm.EvidenceVerifier;
import com.example.disclosurereview.rule.domain.RuleAction;
import com.example.disclosurereview.rule.domain.RuleExecutionStatus;
import com.example.disclosurereview.rule.domain.SemanticRuleCheck;
import com.example.disclosurereview.rule.domain.SemanticRuleResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LlmReviewStageSemanticRuleTest {

    @Test
    void highConfidenceNegativeResponseKeepsModelExplanation() {
        LlmReviewStage stage = stage();

        var result = stage.semanticExecutionResult(check(0.8), List.of(
                new SemanticRuleResponse("NEW_LLM_CAPITAL_GUARANTEE", false, 0.8, 1, null,
                        "正文仅包含非保本和不保证本金等否定表述", null)), List.of());

        assertThat(result.status()).isEqualTo(RuleExecutionStatus.NOT_HIT);
        assertThat(result.detail()).contains("confidence=0.8").contains("否定表述");
    }

    @Test
    void lowConfidenceNegativeResponseIsIndeterminateInsteadOfNotHit() {
        LlmReviewStage stage = stage();

        var result = stage.semanticExecutionResult(check(0.8), List.of(
                new SemanticRuleResponse("NEW_LLM_CAPITAL_GUARANTEE", false, 0.7, null, null,
                        "未发现违规", null)), List.of());

        assertThat(result.status()).isEqualTo(RuleExecutionStatus.INDETERMINATE);
        assertThat(result.detail()).contains("LOW_CONFIDENCE").contains("minConfidence=0.8");
    }

    private SemanticRuleCheck check(double minConfidence) {
        return new SemanticRuleCheck(12L, "NEW_LLM_CAPITAL_GUARANTEE", 15L, "v1", 109L,
                "LLM_POLICY", "识别正向保本承诺", "否定语境不违规", "JSON", minConfidence,
                RuleAction.defaultAction(), List.of());
    }

    private LlmReviewStage stage() {
        return new LlmReviewStage(null, null, null, null, null, null, null, null,
                null, null, null, null, null, new EvidenceVerifier());
    }
}
