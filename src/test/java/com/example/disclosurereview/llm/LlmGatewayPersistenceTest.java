package com.example.disclosurereview.llm;

import com.example.disclosurereview.config.LlmProperties;
import com.example.disclosurereview.exception.LlmException;
import com.example.disclosurereview.model.DocumentCategory;
import com.example.disclosurereview.model.ReviewStage;
import com.example.disclosurereview.model.ReviewTaskStatus;
import com.example.disclosurereview.persistence.entity.LlmCallAttemptEntity;
import com.example.disclosurereview.persistence.entity.ModelCallRecordEntity;
import com.example.disclosurereview.persistence.entity.ReviewTaskEntity;
import com.example.disclosurereview.persistence.repository.LlmCallAttemptJpaRepository;
import com.example.disclosurereview.persistence.repository.LlmModelConfigJpaRepository;
import com.example.disclosurereview.persistence.repository.ModelCallRecordJpaRepository;
import com.example.disclosurereview.persistence.repository.ReviewTaskJpaRepository;
import com.example.disclosurereview.service.ReviewTaskQueryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(showSql = false)
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class LlmGatewayPersistenceTest {

    @Autowired
    private LlmModelConfigJpaRepository modelRepository;

    @Autowired
    private LlmCallAttemptJpaRepository attemptRepository;

    @Autowired
    private ReviewTaskJpaRepository taskRepository;

    @Autowired
    private ModelCallRecordJpaRepository modelCallRepository;

    @Test
    void recordsFallbackAttemptsAndActualSuccessModelWithTaskContextAndUsage() {
        ObjectMapper objectMapper = new ObjectMapper();
        ReviewTaskEntity task = taskRepository.saveAndFlush(newTask());
        LlmGateway gateway = new LlmGateway(
                List.of(new FakeFallbackAdapter()),
                new LlmProperties(),
                modelRepository,
                attemptRepository,
                taskRepository,
                modelCallRepository,
                objectMapper);

        LlmGatewayResponse<String> response = gateway.chatCompletion(
                new LlmCallContext(task.getId(), ReviewStage.LLM_REVIEWING, "COMBINED_REVIEW",
                        null, null, 1, 1, 3),
                "system",
                "user",
                raw -> raw);

        assertThat(response.result()).isEqualTo("{\"ok\":true}");
        assertThat(response.modelName()).isEqualTo("mimo-v2.5");
        assertThat(response.usage().inputTokens()).isEqualTo(123);
        assertThat(response.usage().outputTokens()).isEqualTo(45);
        assertThat(response.usage().cacheHitTokens()).isEqualTo(6);

        List<LlmCallAttemptEntity> attempts = attemptRepository.findByTask_IdOrderById(task.getId());
        assertThat(attempts).hasSize(3);
        assertThat(attempts).extracting(LlmCallAttemptEntity::getCallStatus)
                .containsExactly("FAILED", "FAILED", "SUCCESS");
        assertThat(attempts.get(0).getModelName()).isEqualTo("deepseek-v4-flash");
        assertThat(attempts.get(2)).satisfies(success -> {
            assertThat(success.getTask().getId()).isEqualTo(task.getId());
            assertThat(success.getStage()).isEqualTo("LLM_REVIEWING");
            assertThat(success.getOperationType()).isEqualTo("COMBINED_REVIEW");
            assertThat(success.getChunkIndex()).isEqualTo(1);
            assertThat(success.getPageFrom()).isEqualTo(1);
            assertThat(success.getPageTo()).isEqualTo(3);
            assertThat(success.getInputTokenCount()).isEqualTo(123);
            assertThat(success.getOutputTokenCount()).isEqualTo(45);
            assertThat(success.getCacheHitTokenCount()).isEqualTo(6);
        });

        List<ModelCallRecordEntity> records = modelCallRepository.findByTaskIdOrderById(task.getId());
        assertThat(records).singleElement().satisfies(record -> {
            assertThat(record.getTask().getId()).isEqualTo(task.getId());
            assertThat(record.getModelConfig()).isNotNull();
            assertThat(record.getProvider()).isEqualTo("xiaomi-mimo");
            assertThat(record.getModelName()).isEqualTo("mimo-v2.5");
            assertThat(record.getStage()).isEqualTo("LLM_REVIEWING");
            assertThat(record.getOperationType()).isEqualTo("COMBINED_REVIEW");
            assertThat(record.getInputTokenCount()).isEqualTo(123);
            assertThat(record.getOutputTokenCount()).isEqualTo(45);
            assertThat(record.getCacheHitTokenCount()).isEqualTo(6);
        });

        ReviewTaskQueryService queryService = new ReviewTaskQueryService(
                taskRepository,
                null,
                null,
                null,
                null,
                attemptRepository,
                modelCallRepository,
                null,
                null,
                objectMapper);
        assertThat(queryService.llmUsage(task.getId()).inputTokens()).isEqualTo(123);
        assertThat(queryService.llmUsage(task.getId()).outputTokens()).isEqualTo(45);
        assertThat(queryService.llmUsage(task.getId()).cacheHitTokens()).isEqualTo(6);
        assertThat(queryService.llmUsage(task.getId()).callCount()).isEqualTo(3);
        assertThat(queryService.llmCalls(task.getId())).hasSize(3);
    }

    @Test
    void responseUsageAggregatesTokenBearingInvalidAttemptsBeforeFallbackSuccess() {
        ObjectMapper objectMapper = new ObjectMapper();
        ReviewTaskEntity task = taskRepository.saveAndFlush(newTask());
        LlmGateway gateway = new LlmGateway(
                List.of(new InvalidJsonFallbackAdapter()), new LlmProperties(), modelRepository,
                attemptRepository, taskRepository, modelCallRepository, objectMapper);

        LlmGatewayResponse<String> response = gateway.chatCompletion(
                new LlmCallContext(task.getId(), ReviewStage.LLM_REVIEWING, "BACKTEST_USAGE",
                        null, null, 1, 1, 1),
                "system", "user", raw -> {
                    if (!raw.startsWith("{")) throw new LlmException("invalid JSON");
                    return raw;
                });

        assertThat(response.modelName()).isEqualTo("mimo-v2.5");
        assertThat(response.usage().inputTokens()).isEqualTo(40);
        assertThat(response.usage().outputTokens()).isEqualTo(16);
        assertThat(response.usage().cacheHitTokens()).isEqualTo(7);
        assertThat(attemptRepository.findByTask_IdOrderById(task.getId()))
                .extracting(LlmCallAttemptEntity::getCallStatus)
                .containsExactly("FAILED", "FAILED", "SUCCESS");
        assertThat(modelCallRepository.findByTaskIdOrderById(task.getId())).singleElement().satisfies(record -> {
            assertThat(record.getModelName()).isEqualTo("mimo-v2.5");
            assertThat(record.getInputTokenCount()).isEqualTo(20);
            assertThat(record.getOutputTokenCount()).isEqualTo(6);
        });
    }

    private ReviewTaskEntity newTask() {
        ReviewTaskEntity task = new ReviewTaskEntity();
        task.setTaskNo("REV-20260728-LLM001");
        task.setOriginalFileName("SGN22555_投资协议书.pdf");
        task.setStoredFileName("stored.pdf");
        task.setFilePath("2026/07/28/stored.pdf");
        task.setFileHash("c".repeat(64));
        task.setDocumentCategory(DocumentCategory.PROTOCOL);
        task.setStatus(ReviewTaskStatus.CREATED);
        task.setReviewVersion("v1");
        task.setIdempotencyKey("c".repeat(64) + "::v1");
        task.setCreatedAt(Instant.now());
        return task;
    }

    private static class FakeFallbackAdapter implements LlmProviderAdapter {

        @Override
        public boolean supports(String providerType) {
            return true;
        }

        @Override
        public LlmProviderResponse chatCompletion(LlmClient.RuntimeModel runtimeModel,
                                                  String systemPrompt,
                                                  String userPrompt) {
            if ("deepseek-v4-flash".equals(runtimeModel.modelName())) {
                throw new LlmException("simulated primary failure");
            }
            return new LlmProviderResponse(
                    "{\"ok\":true}",
                    "{\"choices\":[],\"usage\":{\"prompt_tokens\":123,\"completion_tokens\":45}}",
                    new LlmUsage(123, 45, 6,
                            "{\"prompt_tokens\":123,\"completion_tokens\":45,\"cache_hit_tokens\":6}"));
        }
    }

    private static class InvalidJsonFallbackAdapter implements LlmProviderAdapter {
        @Override public boolean supports(String providerType) { return true; }

        @Override
        public LlmProviderResponse chatCompletion(LlmClient.RuntimeModel runtimeModel,
                                                  String systemPrompt,
                                                  String userPrompt) {
            if ("deepseek-v4-flash".equals(runtimeModel.modelName())) {
                return new LlmProviderResponse("invalid", "invalid", new LlmUsage(10, 5, 2, "{}"));
            }
            return new LlmProviderResponse("{\"ok\":true}", "{\"ok\":true}",
                    new LlmUsage(20, 6, 3, "{}"));
        }
    }
}
