package com.example.disclosurereview.pipeline;

import com.example.disclosurereview.persistence.entity.ReviewTaskContextEntity;
import com.example.disclosurereview.persistence.entity.ReviewTaskEntity;
import com.example.disclosurereview.persistence.repository.ReviewTaskContextJpaRepository;
import com.example.disclosurereview.persistence.repository.ReviewTaskJpaRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
public class ReviewTaskContextStore {

    private final ReviewTaskContextJpaRepository contextRepository;
    private final ReviewTaskJpaRepository taskRepository;
    private final ObjectMapper objectMapper;

    public ReviewTaskContextStore(ReviewTaskContextJpaRepository contextRepository,
                                  ReviewTaskJpaRepository taskRepository,
                                  ObjectMapper objectMapper) {
        this.contextRepository = contextRepository;
        this.taskRepository = taskRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public ObjectNode load(Long taskId) {
        return contextRepository.findByTask_Id(taskId)
                .map(entity -> parse(entity.getContextJson()))
                .orElseGet(objectMapper::createObjectNode);
    }

    @Transactional(readOnly = true)
    public <T> Optional<T> read(Long taskId, String key, TypeReference<T> type) {
        JsonNode value = load(taskId).path(key);
        if (value.isMissingNode() || value.isNull()) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.convertValue(value, type));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    @Transactional
    public void put(Long taskId, String key, Object value) {
        ObjectNode root = load(taskId);
        root.set(key, objectMapper.valueToTree(value));
        save(taskId, root);
    }

    @Transactional
    public void remove(Long taskId, String... keys) {
        ObjectNode root = load(taskId);
        for (String key : keys) {
            root.remove(key);
        }
        save(taskId, root);
    }

    @Transactional
    public void save(Long taskId, ObjectNode root) {
        ReviewTaskContextEntity entity = contextRepository.findByTask_Id(taskId)
                .orElseGet(() -> newEntity(taskId));
        entity.setContextJson(write(root));
        entity.setUpdatedAt(Instant.now());
        contextRepository.save(entity);
    }

    private ReviewTaskContextEntity newEntity(Long taskId) {
        ReviewTaskEntity task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + taskId));
        ReviewTaskContextEntity entity = new ReviewTaskContextEntity();
        entity.setTask(task);
        entity.setContextJson("{}");
        Instant now = Instant.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return entity;
    }

    private ObjectNode parse(String json) {
        try {
            JsonNode node = objectMapper.readTree(json == null || json.isBlank() ? "{}" : json);
            return node instanceof ObjectNode objectNode ? objectNode : objectMapper.createObjectNode();
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }

    private String write(ObjectNode root) {
        try {
            return objectMapper.writeValueAsString(root == null ? objectMapper.createObjectNode() : root);
        } catch (Exception e) {
            return "{}";
        }
    }
}
