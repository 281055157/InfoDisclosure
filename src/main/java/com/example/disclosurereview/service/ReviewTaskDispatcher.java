package com.example.disclosurereview.service;

import com.example.disclosurereview.config.ReviewProperties;
import com.example.disclosurereview.model.ReviewStage;
import com.example.disclosurereview.persistence.entity.ReviewTaskEventEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class ReviewTaskDispatcher {

    private static final Logger log = LoggerFactory.getLogger(ReviewTaskDispatcher.class);

    private final ReviewTaskEventService eventService;
    private final RabbitTemplate rabbitTemplate;
    private final ReviewProperties properties;

    public ReviewTaskDispatcher(ReviewTaskEventService eventService,
                                RabbitTemplate rabbitTemplate,
                                ReviewProperties properties) {
        this.eventService = eventService;
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
    }

    public void process(Long taskId) {
        dispatch(taskId, ReviewStage.DOCUMENT_PARSING, "REVIEW_REQUESTED", "{}");
    }

    public boolean retry(Long taskId, ReviewStage stage) {
        return dispatch(taskId, stage == null ? ReviewStage.LLM_REVIEWING : stage, "REVIEW_RETRY_REQUESTED", "{}");
    }

    public boolean dispatchStage(Long taskId, ReviewStage stage, String eventType, String payloadJson) {
        return dispatch(taskId, stage, eventType, payloadJson == null ? "{}" : payloadJson);
    }

    private boolean dispatch(Long taskId, ReviewStage stage, String eventType, String payloadJson) {
        ReviewTaskEventService.StageEventLease lease =
                eventService.createOrReuseActiveForDispatch(taskId, stage, eventType, payloadJson);
        ReviewTaskEventEntity event = lease.event();
        afterCommitOrNow(() -> {
            if (!lease.newlyCreated()) {
                log.info("Reuse active review task event {}, task={}, stage={}, status={}",
                        event.getId(), taskId, event.getStage(), event.getEventStatus());
                return;
            }
            try {
                publish(event.getId(), taskId, event.getStage(), event.getEventType());
            } catch (Exception e) {
                log.warn("Failed to publish review task event {}, it remains pending: {}", event.getId(), e.getMessage());
            }
        });
        return lease.newlyCreated();
    }

    private void publish(Long eventId, Long taskId, ReviewStage stage, String eventType) {
        rabbitTemplate.convertAndSend(
                properties.getRabbitmq().getExchange(),
                properties.getRabbitmq().getStageRoutingKey(),
                new ReviewTaskStageMessage(eventId, taskId, stage, eventType, 1));
        eventService.markPublished(eventId);
    }

    private void afterCommitOrNow(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
