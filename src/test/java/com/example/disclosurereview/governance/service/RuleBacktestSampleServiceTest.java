package com.example.disclosurereview.governance.service;

import com.example.disclosurereview.config.FeedbackGovernanceProperties;
import com.example.disclosurereview.governance.persistence.entity.RuleFeedbackGovernanceGroupEntity;
import com.example.disclosurereview.model.ReviewIssueStatus;
import com.example.disclosurereview.persistence.entity.ReviewIssueEntity;
import com.example.disclosurereview.persistence.entity.ReviewRuleExecutionEntity;
import com.example.disclosurereview.persistence.entity.ReviewRuleFeedbackEntity;
import com.example.disclosurereview.persistence.entity.ReviewRuleVersionEntity;
import com.example.disclosurereview.persistence.entity.ReviewTaskEntity;
import com.example.disclosurereview.persistence.repository.ReviewIssueJpaRepository;
import com.example.disclosurereview.persistence.repository.ReviewRuleExecutionJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RuleBacktestSampleServiceTest {
    @Test
    void semanticSamplesPreferThreeFalsePositivesOneConfirmedAndOneNormal() {
        FeedbackGovernanceGroupService groups = mock(FeedbackGovernanceGroupService.class);
        ReviewIssueJpaRepository issues = mock(ReviewIssueJpaRepository.class);
        ReviewRuleExecutionJpaRepository executions = mock(ReviewRuleExecutionJpaRepository.class);
        FeedbackGovernanceProperties properties = new FeedbackGovernanceProperties();
        RuleFeedbackGovernanceGroupEntity group = new RuleFeedbackGovernanceGroupEntity();
        ReflectionTestUtils.setField(group, "id", 9L);
        ReviewRuleVersionEntity version = new ReviewRuleVersionEntity();
        ReflectionTestUtils.setField(version, "id", 19L);
        group.setRuleVersionEntity(version);

        List<ReviewRuleFeedbackEntity> feedbacks = new ArrayList<>();
        for (long id = 1; id <= 5; id++) {
            ReviewRuleFeedbackEntity feedback = new ReviewRuleFeedbackEntity();
            ReflectionTestUtils.setField(feedback, "id", id);
            feedback.setTask(task(id));
            feedbacks.add(feedback);
        }
        when(groups.feedbacks(9L)).thenReturn(feedbacks);
        ReviewIssueEntity confirmed = new ReviewIssueEntity();
        confirmed.setTask(task(6L));
        when(issues.findByRuleVersionIdAndIssueStatusOrderByCreatedAtDesc(
                eq(19L), eq(ReviewIssueStatus.CONFIRMED), any(Pageable.class))).thenReturn(List.of(confirmed));
        ReviewRuleExecutionEntity normal = new ReviewRuleExecutionEntity();
        normal.setTask(task(7L));
        when(executions.findByRuleVersionIdAndMatchedOrderByCreatedAtDesc(
                eq(19L), eq(false), any(Pageable.class))).thenReturn(List.of(normal));

        List<RuleBacktestSampleService.BacktestSample> selected = new RuleBacktestSampleService(
                groups, issues, executions, properties).semanticSamples(group, 5);

        assertThat(selected).extracting(RuleBacktestSampleService.BacktestSample::sampleType)
                .containsExactly("FALSE_POSITIVE", "FALSE_POSITIVE", "FALSE_POSITIVE", "CONFIRMED_POSITIVE", "NORMAL");
        assertThat(selected).extracting(RuleBacktestSampleService.BacktestSample::taskId)
                .containsExactly(1L, 2L, 3L, 6L, 7L);
    }

    @Test
    void semanticSamplesKeepDistinctFeedbacksFromSameTask() {
        FeedbackGovernanceGroupService groups = mock(FeedbackGovernanceGroupService.class);
        ReviewIssueJpaRepository issues = mock(ReviewIssueJpaRepository.class);
        ReviewRuleExecutionJpaRepository executions = mock(ReviewRuleExecutionJpaRepository.class);
        FeedbackGovernanceProperties properties = new FeedbackGovernanceProperties();
        RuleFeedbackGovernanceGroupEntity group = new RuleFeedbackGovernanceGroupEntity();
        ReflectionTestUtils.setField(group, "id", 9L);
        ReviewRuleVersionEntity version = new ReviewRuleVersionEntity();
        ReflectionTestUtils.setField(version, "id", 19L);
        group.setRuleVersionEntity(version);

        ReviewTaskEntity sharedTask = task(14L);
        List<ReviewRuleFeedbackEntity> feedbacks = new ArrayList<>();
        for (long id = 1; id <= 5; id++) {
            ReviewIssueEntity issue = new ReviewIssueEntity();
            ReflectionTestUtils.setField(issue, "id", 100L + id);
            issue.setTask(sharedTask);
            issue.setPageNumber((int) id);
            issue.setEvidenceText("误报证据-" + id);
            ReviewRuleFeedbackEntity feedback = new ReviewRuleFeedbackEntity();
            ReflectionTestUtils.setField(feedback, "id", id);
            feedback.setTask(sharedTask);
            feedback.setIssue(issue);
            feedbacks.add(feedback);
        }
        when(groups.feedbacks(9L)).thenReturn(feedbacks);
        when(issues.findByRuleVersionIdAndIssueStatusOrderByCreatedAtDesc(
                eq(19L), eq(ReviewIssueStatus.CONFIRMED), any(Pageable.class))).thenReturn(List.of());
        when(executions.findByRuleVersionIdAndMatchedOrderByCreatedAtDesc(
                eq(19L), eq(false), any(Pageable.class))).thenReturn(List.of());

        List<RuleBacktestSampleService.BacktestSample> selected = new RuleBacktestSampleService(
                groups, issues, executions, properties).semanticSamples(group, 5);

        assertThat(selected).hasSize(5);
        assertThat(selected).extracting(RuleBacktestSampleService.BacktestSample::sampleId)
                .containsExactly("feedback:1", "feedback:2", "feedback:3", "feedback:4", "feedback:5");
        assertThat(selected).extracting(RuleBacktestSampleService.BacktestSample::taskId)
                .containsOnly(14L);
        assertThat(selected).extracting(RuleBacktestSampleService.BacktestSample::targetEvidenceText)
                .containsExactly("误报证据-1", "误报证据-2", "误报证据-3", "误报证据-4", "误报证据-5");
    }

    private ReviewTaskEntity task(Long id) {
        ReviewTaskEntity task = new ReviewTaskEntity();
        ReflectionTestUtils.setField(task, "id", id);
        return task;
    }
}
