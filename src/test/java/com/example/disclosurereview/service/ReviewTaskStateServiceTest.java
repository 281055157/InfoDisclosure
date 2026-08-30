package com.example.disclosurereview.service;

import com.example.disclosurereview.model.ReviewTaskStatus;
import com.example.disclosurereview.persistence.entity.ReviewTaskEntity;
import com.example.disclosurereview.persistence.repository.ReviewTaskJpaRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReviewTaskStateServiceTest {

    @Test
    void allowsCreatedToParsing() {
        ReviewTaskEntity task = task(ReviewTaskStatus.CREATED);
        ReviewTaskJpaRepository repository = mock(ReviewTaskJpaRepository.class);
        when(repository.findById(anyLong())).thenReturn(Optional.of(task));
        when(repository.save(any(ReviewTaskEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        AuditLogService auditLogService = mock(AuditLogService.class);
        ReviewTaskStateService stateService = new ReviewTaskStateService(repository, auditLogService);

        ReviewTaskEntity updated = stateService.transition(1L, ReviewTaskStatus.PARSING, "start");

        assertThat(updated.getStatus()).isEqualTo(ReviewTaskStatus.PARSING);
        assertThat(updated.getStartedAt()).isNotNull();
    }

    @Test
    void rejectsCreatedToManualApproved() {
        ReviewTaskEntity task = task(ReviewTaskStatus.CREATED);
        ReviewTaskJpaRepository repository = mock(ReviewTaskJpaRepository.class);
        when(repository.findById(anyLong())).thenReturn(Optional.of(task));
        AuditLogService auditLogService = mock(AuditLogService.class);
        ReviewTaskStateService stateService = new ReviewTaskStateService(repository, auditLogService);

        assertThatThrownBy(() -> stateService.transition(1L, ReviewTaskStatus.MANUAL_APPROVED, "bad"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("非法任务状态转换");
    }

    private ReviewTaskEntity task(ReviewTaskStatus status) {
        ReviewTaskEntity task = new ReviewTaskEntity();
        task.setStatus(status);
        task.setCreatedAt(Instant.now());
        return task;
    }
}
