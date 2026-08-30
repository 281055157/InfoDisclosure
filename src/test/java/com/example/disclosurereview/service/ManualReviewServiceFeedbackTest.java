package com.example.disclosurereview.service;

import com.example.disclosurereview.config.ReviewProperties;
import com.example.disclosurereview.dto.ReviewTaskDtos.IssueUpdateRequest;
import com.example.disclosurereview.model.ReviewIssueStatus;
import com.example.disclosurereview.persistence.entity.ReviewIssueEntity;
import com.example.disclosurereview.persistence.entity.ReviewRuleFeedbackEntity;
import com.example.disclosurereview.persistence.entity.ReviewTaskEntity;
import com.example.disclosurereview.persistence.repository.ManualReviewJpaRepository;
import com.example.disclosurereview.persistence.repository.ReviewIssueJpaRepository;
import com.example.disclosurereview.persistence.repository.ReviewRuleFeedbackJpaRepository;
import com.example.disclosurereview.persistence.repository.ReviewTaskJpaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ManualReviewServiceFeedbackTest {

    @Test
    void falsePositiveFeedbackUsesProducingRuleRatherThanFindingCategory() {
        ReviewTaskJpaRepository tasks = mock(ReviewTaskJpaRepository.class);
        ManualReviewJpaRepository manuals = mock(ManualReviewJpaRepository.class);
        ReviewIssueJpaRepository issues = mock(ReviewIssueJpaRepository.class);
        ReviewRuleFeedbackJpaRepository feedbacks = mock(ReviewRuleFeedbackJpaRepository.class);
        ReviewTaskStateService states = mock(ReviewTaskStateService.class);
        AuditLogService audits = mock(AuditLogService.class);
        ReviewTaskDispatcher dispatcher = mock(ReviewTaskDispatcher.class);
        MeterRegistry meters = mock(MeterRegistry.class);
        ManualReviewService service = new ManualReviewService(tasks, manuals, issues, feedbacks, states, audits,
                dispatcher, new ReviewProperties(), new ObjectMapper(), meters);

        ReviewTaskEntity task = new ReviewTaskEntity();
        ReviewIssueEntity issue = new ReviewIssueEntity();
        issue.setTask(task);
        issue.setIssueCode("CONTENT_LOGIC_CONFLICT");
        issue.setRuleCode("TEST_REGEX_CAPITAL_GUARANTEE");
        issue.setRuleVersionId(14L);
        issue.setRuleExecutionId(99L);
        issue.setIssueStatus(ReviewIssueStatus.OPEN);
        when(issues.findByIdAndTaskId(64L, 14L)).thenReturn(Optional.of(issue));
        when(feedbacks.findFirstByIssue_IdAndFeedbackTypeOrderByCreatedAtDesc(any(), eq("FALSE_POSITIVE")))
                .thenReturn(Optional.empty());
        when(feedbacks.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.updateIssue(14L, 64L, new IssueUpdateRequest(ReviewIssueStatus.FALSE_POSITIVE, "模板语句被误报"));

        ArgumentCaptor<ReviewRuleFeedbackEntity> captured = ArgumentCaptor.forClass(ReviewRuleFeedbackEntity.class);
        verify(feedbacks).save(captured.capture());
        assertThat(captured.getValue().getRuleCode()).isEqualTo("TEST_REGEX_CAPITAL_GUARANTEE");
        assertThat(captured.getValue().getAggregationKey()).startsWith("TEST_REGEX_CAPITAL_GUARANTEE|14|FALSE_POSITIVE|");
    }
}
