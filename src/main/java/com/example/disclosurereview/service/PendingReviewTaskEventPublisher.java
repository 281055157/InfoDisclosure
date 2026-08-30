package com.example.disclosurereview.service;

import com.example.disclosurereview.config.ReviewProperties;
import com.example.disclosurereview.persistence.entity.ReviewTaskEventEntity;
import com.example.disclosurereview.persistence.repository.ReviewTaskEventJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "review.rabbitmq", name = "pending-publish-enabled", havingValue = "true", matchIfMissing = true)
public class PendingReviewTaskEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(PendingReviewTaskEventPublisher.class);

    private final ReviewTaskEventJpaRepository eventRepository;
    private final ReviewTaskEventService eventService;
    private final RabbitTemplate rabbitTemplate;
    private final ReviewProperties properties;

    public PendingReviewTaskEventPublisher(ReviewTaskEventJpaRepository eventRepository,
                                           ReviewTaskEventService eventService,
                                           RabbitTemplate rabbitTemplate,
                                           ReviewProperties properties) {
        this.eventRepository = eventRepository;
        this.eventService = eventService;
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${review.rabbitmq.pending-publish-delay-ms:10000}")
    @Transactional
    public void publishPending() {
        for (ReviewTaskEventEntity event : eventRepository
                .findTop50ByEventStatusOrderByCreatedAtAsc(ReviewTaskEventService.STATUS_PENDING)) {
            try {
                rabbitTemplate.convertAndSend(
                        properties.getRabbitmq().getExchange(),
                        properties.getRabbitmq().getStageRoutingKey(),
                        new ReviewTaskStageMessage(event.getId(), event.getTask().getId(),
                                event.getStage(), event.getEventType(), event.getRetryCount() + 1));
                eventService.markPublished(event.getId());
            } catch (Exception e) {
                log.warn("Failed to publish pending review task event {}: {}", event.getId(), e.getMessage());
            }
        }
    }
}
