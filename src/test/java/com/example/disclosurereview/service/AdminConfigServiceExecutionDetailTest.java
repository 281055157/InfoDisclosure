package com.example.disclosurereview.service;

import com.example.disclosurereview.persistence.entity.ReviewRuleExecutionEntity;
import com.example.disclosurereview.persistence.repository.ReviewRuleExecutionJpaRepository;
import com.example.disclosurereview.rule.executor.RuleJsonSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminConfigServiceExecutionDetailTest {

    @Test
    void executionApiReturnsPersistedReasonAndFallbackForHistoricalNotHit() {
        ReviewRuleExecutionJpaRepository executions = mock(ReviewRuleExecutionJpaRepository.class);
        ReviewRuleExecutionEntity explained = execution(1L,
                "{\"status\":\"NOT_HIT\",\"detail\":\"confidence=0.8；否定语境不违规\"}");
        ReviewRuleExecutionEntity historical = execution(2L,
                "{\"status\":\"NOT_HIT\",\"detail\":null}");
        when(executions.findByRuleIdOrderByCreatedAtDesc(12L)).thenReturn(List.of(explained, historical));
        AdminConfigService service = new AdminConfigService(null, null, executions, null, null, null,
                null, null, new RuleJsonSupport(new ObjectMapper()), null, null);

        var result = service.ruleExecutions(12L);

        assertThat(result).extracting(row -> row.resultDetail()).containsExactly(
                "confidence=0.8；否定语境不违规",
                "规则已执行，未发现满足命中条件的违规内容");
    }

    private ReviewRuleExecutionEntity execution(Long id, String resultJson) {
        ReviewRuleExecutionEntity entity = new ReviewRuleExecutionEntity();
        org.springframework.test.util.ReflectionTestUtils.setField(entity, "id", id);
        entity.setRuleCode("NEW_LLM_CAPITAL_GUARANTEE");
        entity.setRuleVersion("v1");
        entity.setRuleVersionId(15L);
        entity.setExecutionStatus("NOT_HIT");
        entity.setMatched(false);
        entity.setResultJson(resultJson);
        return entity;
    }
}
