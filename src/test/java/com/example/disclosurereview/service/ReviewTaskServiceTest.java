package com.example.disclosurereview.service;

import com.example.disclosurereview.config.ReviewProperties;
import com.example.disclosurereview.dto.ReviewTaskDtos.CreateReviewResponse;
import com.example.disclosurereview.model.DocumentCategory;
import com.example.disclosurereview.persistence.entity.ReviewTaskEntity;
import com.example.disclosurereview.persistence.repository.ReviewTaskJpaRepository;
import com.example.disclosurereview.storage.FileStorageService;
import com.example.disclosurereview.storage.StoredFile;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReviewTaskServiceTest {

    @Test
    void sameFileCreatesIndependentTasksWithoutForceReview() {
        FileStorageService storage = mock(FileStorageService.class);
        ReviewTaskJpaRepository repository = mock(ReviewTaskJpaRepository.class);
        ReviewTaskDispatcher dispatcher = mock(ReviewTaskDispatcher.class);
        ReviewProperties properties = new ReviewProperties();
        properties.setReviewVersion("v-test");
        TaskNoGenerator taskNoGenerator = mock(TaskNoGenerator.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        ReviewTaskService service = new ReviewTaskService(storage, repository, dispatcher, properties,
                taskNoGenerator, auditLogService, new SimpleMeterRegistry());

        when(storage.save(any(), any())).thenReturn(
                storedFile("first.pdf"),
                storedFile("second.pdf"));
        when(taskNoGenerator.next()).thenReturn("REV-1", "REV-2");
        AtomicLong ids = new AtomicLong(10);
        when(repository.saveAndFlush(any(ReviewTaskEntity.class))).thenAnswer(invocation -> {
            ReviewTaskEntity task = invocation.getArgument(0);
            ReflectionTestUtils.setField(task, "id", ids.incrementAndGet());
            return task;
        });

        MockMultipartFile firstUpload = new MockMultipartFile(
                "file", "same.pdf", "application/pdf", "same-content".getBytes());
        MockMultipartFile secondUpload = new MockMultipartFile(
                "file", "same.pdf", "application/pdf", "same-content".getBytes());

        CreateReviewResponse first = service.create(firstUpload, null, DocumentCategory.AUTO,
                null, null, false, null, null, null);
        CreateReviewResponse second = service.create(secondUpload, null, DocumentCategory.AUTO,
                null, null, false, null, null, null);

        assertThat(first.taskId()).isNotEqualTo(second.taskId());
        assertThat(first.duplicate()).isFalse();
        assertThat(second.duplicate()).isFalse();
        verify(repository, never()).findByIdempotencyKey(any());
        verify(repository, times(2)).saveAndFlush(any(ReviewTaskEntity.class));
        verify(dispatcher).process(first.taskId());
        verify(dispatcher).process(second.taskId());
    }

    private StoredFile storedFile(String storedName) {
        return new StoredFile("same.pdf", storedName, "reviews/" + storedName,
                "reviews/" + storedName, "same-sha256", 12L);
    }
}
