package com.example.disclosurereview.persistence;

import com.example.disclosurereview.model.DocumentCategory;
import com.example.disclosurereview.model.ReviewTaskStatus;
import com.example.disclosurereview.persistence.entity.ReviewTaskEntity;
import com.example.disclosurereview.persistence.repository.ReviewTaskContextJpaRepository;
import com.example.disclosurereview.persistence.repository.ReviewTaskJpaRepository;
import com.example.disclosurereview.pipeline.ReviewTaskContextStore;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(showSql = false)
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class ReviewTaskJpaRepositoryTest {

    @Autowired
    private ReviewTaskJpaRepository repository;

    @Autowired
    private ReviewTaskContextJpaRepository contextRepository;

    @Test
    void flywayMigrationCreatesReviewTaskTable() {
        ReviewTaskEntity saved = repository.saveAndFlush(newTask("REV-20260724-000001", "a".repeat(64)));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getVersion()).isNotNull();
        assertThat(repository.findByIdempotencyKey(saved.getIdempotencyKey())).isPresent();
    }

    @Test
    void contextStorePersistsAndReadsTaskJsonWithoutDocumentTextDuplication() {
        ReviewTaskEntity task = repository.saveAndFlush(newTask("REV-20260724-000002", "b".repeat(64)));
        ReviewTaskContextStore store = new ReviewTaskContextStore(contextRepository, repository, new ObjectMapper());

        store.put(task.getId(), "declaration", Map.of(
                "productCode", "SGN22555",
                "documentType", "投资协议书"));
        store.put(task.getId(), "productMatch", Map.of(
                "matched", true,
                "productCode", "SGN22555"));
        store.remove(task.getId(), "documentText");

        Map<String, Object> declaration = store.read(task.getId(), "declaration", new TypeReference<Map<String, Object>>() {
        }).orElseThrow();

        assertThat(declaration).containsEntry("productCode", "SGN22555");
        assertThat(store.load(task.getId()).has("documentText")).isFalse();
        assertThat(contextRepository.findByTask_Id(task.getId()))
                .isPresent()
                .get()
                .satisfies(context -> assertThat(context.getContextJson()).contains("productMatch"));
    }

    private ReviewTaskEntity newTask(String taskNo, String hash) {
        ReviewTaskEntity task = new ReviewTaskEntity();
        task.setTaskNo(taskNo);
        task.setOriginalFileName("ZYJYG0053A_产品说明书.pdf");
        task.setStoredFileName("stored.pdf");
        task.setFilePath("2026/07/24/stored.pdf");
        task.setFileHash(hash);
        task.setDocumentCategory(DocumentCategory.PROTOCOL);
        task.setStatus(ReviewTaskStatus.CREATED);
        task.setReviewVersion("v1");
        task.setIdempotencyKey(hash + "::v1");
        task.setCreatedAt(Instant.now());
        return task;
    }
}
