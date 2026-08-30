package com.example.disclosurereview.service;

import com.example.disclosurereview.TestPdfFactory;
import com.example.disclosurereview.dto.ReviewTaskDtos.CreateReviewResponse;
import com.example.disclosurereview.llm.CombinedLlmReviewResult;
import com.example.disclosurereview.llm.LlmCallContext;
import com.example.disclosurereview.llm.LlmGatewayResponse;
import com.example.disclosurereview.llm.LlmReviewService;
import com.example.disclosurereview.llm.LlmUsage;
import com.example.disclosurereview.model.DocumentCategory;
import com.example.disclosurereview.model.LlmReviewResult;
import com.example.disclosurereview.model.ReviewStage;
import com.example.disclosurereview.model.ReviewTaskStatus;
import com.example.disclosurereview.persistence.entity.ReviewTaskEventEntity;
import com.example.disclosurereview.persistence.repository.DocumentPageJpaRepository;
import com.example.disclosurereview.persistence.repository.ReviewTaskEventJpaRepository;
import com.example.disclosurereview.persistence.repository.ReviewTaskJpaRepository;
import com.example.disclosurereview.pipeline.ReviewTaskContextStore;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.InputStream;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doReturn;

@SpringBootTest(properties = {
        "spring.rabbitmq.listener.simple.auto-startup=true",
        "spring.rabbitmq.listener.direct.auto-startup=true",
        "review.rabbitmq.pending-publish-enabled=false",
        "review.storage.root-directory=target/test-review-files/rabbit-pipeline"
})
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class ReviewRabbitPipelineIntegrationTest {

    @Container
    static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.13-management");

    @DynamicPropertySource
    static void rabbitProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", rabbit::getHost);
        registry.add("spring.rabbitmq.port", rabbit::getAmqpPort);
        registry.add("spring.rabbitmq.username", rabbit::getAdminUsername);
        registry.add("spring.rabbitmq.password", rabbit::getAdminPassword);
    }

    @Autowired
    private ReviewTaskService taskService;

    @Autowired
    private ReviewTaskJpaRepository taskRepository;

    @Autowired
    private ReviewTaskEventJpaRepository eventRepository;

    @Autowired
    private DocumentPageJpaRepository pageRepository;

    @Autowired
    private ReviewTaskContextStore contextStore;

    @MockBean
    private LlmReviewService llmReviewService;

    @Test
    void rabbitMqPipelineRunsAllStagesWithPersistedContextAndSinglePdfParse() throws Exception {
        doReturn(new LlmGatewayResponse<>(
                new CombinedLlmReviewResult(LlmReviewResult.empty(), List.of()),
                null,
                "mock-provider",
                "mock-model",
                LlmUsage.empty()))
                .when(llmReviewService)
                .reviewCombined(
                        anyList(),
                        nullable(String.class),
                        nullable(String.class),
                        nullable(String.class),
                        nullable(String.class),
                        nullable(String.class),
                        nullable(String.class),
                        nullable(String.class),
                        nullable(String.class),
                        nullable(String.class),
                        nullable(String.class),
                        nullable(String.class),
                        nullable(String.class),
                        nullable(String.class),
                        anyList(),
                        any(LlmCallContext.class));

        CreateReviewResponse response = taskService.create(pdfFile(), null, DocumentCategory.AUTO,
                null, null, true, null, null, null);

        Awaitility.await()
                .atMost(Duration.ofSeconds(45))
                .pollInterval(Duration.ofMillis(300))
                .untilAsserted(() -> {
                    assertThat(taskRepository.findById(response.taskId()).orElseThrow().getStatus())
                            .isEqualTo(ReviewTaskStatus.WAITING_MANUAL_REVIEW);
                    assertThat(eventRepository.findByTask_IdOrderByCreatedAtAsc(response.taskId()))
                            .isNotEmpty()
                            .allSatisfy(event -> assertThat(event.getEventStatus())
                                    .isEqualTo(ReviewTaskEventService.STATUS_COMPLETED));
                });

        List<ReviewTaskEventEntity> events = eventRepository.findByTask_IdOrderByCreatedAtAsc(response.taskId());
        assertThat(events)
                .extracting(ReviewTaskEventEntity::getStage)
                .containsSubsequence(
                        ReviewStage.DOCUMENT_PARSING,
                        ReviewStage.DECLARATION_RESOLVING,
                        ReviewStage.PRODUCT_MATCHING,
                        ReviewStage.RULE_REVIEWING,
                        ReviewStage.LLM_REVIEWING,
                        ReviewStage.EVIDENCE_VERIFYING,
                        ReviewStage.RESULT_MERGING);
        assertThat(events)
                .allSatisfy(event -> assertThat(event.getEventStatus()).isEqualTo(ReviewTaskEventService.STATUS_COMPLETED));

        assertThat(pageRepository.findByTaskIdOrderByPageNumber(response.taskId()))
                .hasSize(2)
                .extracting(page -> page.getPageNumber())
                .containsExactly(1, 2);

        ObjectNode context = contextStore.load(response.taskId());
        assertThat(context.has("documentParse")).isTrue();
        assertThat(context.has("declaration")).isTrue();
        assertThat(context.has("productMatch")).isTrue();
        assertThat(context.has("ruleReview")).isTrue();
        assertThat(context.has("llmReview")).isTrue();
        assertThat(context.has("evidenceVerification")).isTrue();
        assertThat(context.has("resultMerge")).isTrue();
    }

    private MockMultipartFile pdfFile() {
        try (InputStream in = TestPdfFactory.pdfWithPages(
                "示例理财乙琮融九曜添利180天持有6号行业精选增强理财产品 产品说明书\n产品代码：ZYJYG0053A",
                "第二页 产品代码：ZYJYG0053A")) {
            return new MockMultipartFile("file", "ZYJYG0053A_产品说明书.pdf",
                    "application/pdf", in.readAllBytes());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
