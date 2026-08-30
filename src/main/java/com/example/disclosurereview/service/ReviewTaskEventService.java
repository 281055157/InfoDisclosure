package com.example.disclosurereview.service;

import com.example.disclosurereview.model.ReviewStage;
import com.example.disclosurereview.persistence.entity.ReviewTaskEntity;
import com.example.disclosurereview.persistence.entity.ReviewTaskEventEntity;
import com.example.disclosurereview.persistence.repository.ReviewTaskEventJpaRepository;
import com.example.disclosurereview.persistence.repository.ReviewTaskJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class ReviewTaskEventService {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PUBLISHED = "PUBLISHED";
    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";
    public static final List<String> ACTIVE_STATUSES = List.of(STATUS_PENDING, STATUS_PUBLISHED, STATUS_PROCESSING);

    private final ReviewTaskEventJpaRepository eventRepository;
    private final ReviewTaskJpaRepository taskRepository;

    public ReviewTaskEventService(ReviewTaskEventJpaRepository eventRepository,
                                  ReviewTaskJpaRepository taskRepository) {
        this.eventRepository = eventRepository;
        this.taskRepository = taskRepository;
    }

    @Transactional
    public ReviewTaskEventEntity create(Long taskId, ReviewStage stage, String eventType, String payloadJson) {
        ReviewTaskEntity task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + taskId));
        ReviewTaskEventEntity event = new ReviewTaskEventEntity();
        event.setTask(task);
        event.setStage(stage);
        event.setEventType(eventType);
        event.setEventStatus(STATUS_PENDING);
        event.setPayloadJson(payloadJson);
        event.setRetryCount(0);
        event.setCreatedAt(Instant.now());
        return eventRepository.save(event);
    }

    @Transactional
    public ReviewTaskEventEntity createOrReuseActive(Long taskId,
                                                     ReviewStage stage,
                                                     String eventType,
                                                     String payloadJson) {
        return createOrReuseActiveForDispatch(taskId, stage, eventType, payloadJson).event();
    }

    @Transactional
    public StageEventLease createOrReuseActiveForDispatch(Long taskId,
                                                          ReviewStage stage,
                                                          String eventType,
                                                          String payloadJson) {
        Optional<ReviewTaskEventEntity> active = eventRepository
                .findByTask_IdAndStageAndEventTypeAndEventStatusInOrderByCreatedAtAsc(
                        taskId, stage, eventType, ACTIVE_STATUSES)
                .stream()
                .findFirst();
        if (active.isPresent()) {
            return new StageEventLease(active.get(), false);
        }
        return new StageEventLease(create(taskId, stage, eventType, payloadJson), true);
    }

    @Transactional
    public ReviewTaskEventEntity markPublished(Long eventId) {
        ReviewTaskEventEntity event = get(eventId);
        event.setEventStatus(STATUS_PUBLISHED);
        event.setPublishedAt(Instant.now());
        return eventRepository.save(event);
    }

    @Transactional
    public ReviewTaskEventEntity markProcessing(Long eventId) {
        ReviewTaskEventEntity event = get(eventId);
        if (STATUS_COMPLETED.equals(event.getEventStatus())) {
            return event;
        }
        event.setEventStatus(STATUS_PROCESSING);
        return eventRepository.save(event);
    }

    @Transactional(readOnly = true)
    public boolean isEarliestActiveStageEvent(Long eventId) {
        ReviewTaskEventEntity event = get(eventId);
        List<ReviewTaskEventEntity> active = eventRepository
                .findByTask_IdAndStageAndEventTypeAndEventStatusInOrderByCreatedAtAsc(
                        event.getTask().getId(), event.getStage(), event.getEventType(), ACTIVE_STATUSES);
        return active.isEmpty() || active.get(0).getId().equals(eventId);
    }

    @Transactional
    public void markCompleted(Long eventId) {
        ReviewTaskEventEntity event = get(eventId);
        if (STATUS_COMPLETED.equals(event.getEventStatus())) {
            return;
        }
        event.setEventStatus(STATUS_COMPLETED);
        event.setCompletedAt(Instant.now());
        eventRepository.save(event);
    }

    @Transactional
    public void markFailed(Long eventId, String errorMessage) {
        ReviewTaskEventEntity event = get(eventId);
        if (STATUS_COMPLETED.equals(event.getEventStatus())) {
            return;
        }
        event.setEventStatus(STATUS_FAILED);
        event.setErrorMessage(errorMessage);
        event.setCompletedAt(Instant.now());
        eventRepository.save(event);
    }

    @Transactional(readOnly = true)
    public List<ReviewTaskEventEntity> timeline(Long taskId) {
        return eventRepository.findByTask_IdOrderByCreatedAtAsc(taskId);
    }

    private ReviewTaskEventEntity get(Long eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("任务事件不存在: " + eventId));
    }

    public record StageEventLease(ReviewTaskEventEntity event, boolean newlyCreated) {
    }
}
