package com.example.disclosurereview.governance;

import com.example.disclosurereview.governance.domain.GovernanceGroupStatus;
import com.example.disclosurereview.governance.domain.GovernanceRunStatus;
import com.example.disclosurereview.governance.persistence.entity.RuleFeedbackGovernanceGroupEntity;
import com.example.disclosurereview.governance.persistence.entity.RuleFeedbackGovernanceGroupItemEntity;
import com.example.disclosurereview.governance.persistence.entity.RuleGovernanceRunEntity;
import com.example.disclosurereview.governance.persistence.repository.*;
import com.example.disclosurereview.governance.service.GovernanceRecordDeletionService;
import com.example.disclosurereview.persistence.entity.ReviewRuleFeedbackEntity;
import com.example.disclosurereview.persistence.repository.ReviewRuleFeedbackJpaRepository;
import com.example.disclosurereview.service.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class GovernanceRecordDeletionServiceTest {
    private RuleGovernanceRunJpaRepository runs;
    private RuleFeedbackGovernanceGroupJpaRepository groups;
    private RuleFeedbackGovernanceGroupItemJpaRepository items;
    private RuleChangeProposalJpaRepository proposals;
    private ReviewRuleFeedbackJpaRepository feedbacks;
    private GovernanceRecordDeletionService service;

    @BeforeEach
    void setUp() {
        runs = mock(RuleGovernanceRunJpaRepository.class);
        groups = mock(RuleFeedbackGovernanceGroupJpaRepository.class);
        items = mock(RuleFeedbackGovernanceGroupItemJpaRepository.class);
        proposals = mock(RuleChangeProposalJpaRepository.class);
        feedbacks = mock(ReviewRuleFeedbackJpaRepository.class);
        when(feedbacks.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        service = new GovernanceRecordDeletionService(runs, groups, items, proposals, feedbacks, mock(AuditLogService.class));
    }

    @Test
    void deletingDeferredGroupReleasesFeedbackForRegrouping() {
        RuleGovernanceRunEntity run = run(11L, GovernanceRunStatus.PARTIAL_SUCCESS);
        when(run.getCreatedGroupCount()).thenReturn(1);
        RuleFeedbackGovernanceGroupEntity group = group(7L, run, GovernanceGroupStatus.DEFERRED);
        ReviewRuleFeedbackEntity feedback = new ReviewRuleFeedbackEntity();
        feedback.setProcessStatus("DEFERRED"); feedback.setProcessedAt(Instant.now());
        RuleFeedbackGovernanceGroupItemEntity item = new RuleFeedbackGovernanceGroupItemEntity(); item.setFeedback(feedback);
        when(groups.findLockedById(7L)).thenReturn(Optional.of(group));
        when(items.findByGroup_IdOrderByFeedback_CreatedAtDesc(7L)).thenReturn(List.of(item));

        var result = service.deleteGroup(7L, "tester");

        assertThat(result.releasedFeedbackCount()).isEqualTo(1);
        assertThat(feedback.getProcessStatus()).isEqualTo("PENDING");
        assertThat(feedback.getProcessedAt()).isNull();
        verify(groups).delete(group);
        verify(run).setCreatedGroupCount(0);
    }

    @Test
    void groupWithProposalCannotBeDeleted() {
        RuleFeedbackGovernanceGroupEntity group = group(7L, run(11L, GovernanceRunStatus.SUCCESS), GovernanceGroupStatus.DEFERRED);
        when(groups.findLockedById(7L)).thenReturn(Optional.of(group));
        when(proposals.existsByGovernanceGroup_Id(7L)).thenReturn(true);

        assertThatThrownBy(() -> service.deleteGroup(7L, "tester"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("已形成治理提案");
        verify(groups, never()).delete(any());
    }

    @Test
    void deletingFinishedRunCascadesDeletableGroupsAndReleasesFeedback() {
        RuleGovernanceRunEntity run = run(11L, GovernanceRunStatus.PARTIAL_SUCCESS);
        RuleFeedbackGovernanceGroupEntity group = group(7L, run, GovernanceGroupStatus.FAILED);
        ReviewRuleFeedbackEntity feedback = new ReviewRuleFeedbackEntity(); feedback.setProcessStatus("FAILED");
        RuleFeedbackGovernanceGroupItemEntity item = new RuleFeedbackGovernanceGroupItemEntity(); item.setFeedback(feedback);
        when(runs.findLockedById(11L)).thenReturn(Optional.of(run));
        when(groups.findByGovernanceRun_IdOrderById(11L)).thenReturn(List.of(group));
        when(items.findByGroup_IdOrderByFeedback_CreatedAtDesc(7L)).thenReturn(List.of(item));

        var result = service.deleteRun(11L, "tester");

        assertThat(result.releasedFeedbackCount()).isEqualTo(1);
        assertThat(feedback.getProcessStatus()).isEqualTo("PENDING");
        verify(runs).delete(run);
    }

    private RuleGovernanceRunEntity run(Long id, GovernanceRunStatus status) {
        RuleGovernanceRunEntity run = mock(RuleGovernanceRunEntity.class);
        when(run.getId()).thenReturn(id); when(run.getStatus()).thenReturn(status);
        return run;
    }

    private RuleFeedbackGovernanceGroupEntity group(Long id, RuleGovernanceRunEntity run, GovernanceGroupStatus status) {
        RuleFeedbackGovernanceGroupEntity group = mock(RuleFeedbackGovernanceGroupEntity.class);
        when(group.getId()).thenReturn(id); when(group.getGovernanceRun()).thenReturn(run);
        when(group.getStatus()).thenReturn(status); when(group.getRuleCode()).thenReturn("TEST_RULE");
        return group;
    }
}
