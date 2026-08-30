package com.example.disclosurereview.governance.messaging;

import com.example.disclosurereview.governance.persistence.repository.RuleGovernanceEventJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "feedback-governance.rabbitmq", name = "pending-publish-enabled", havingValue = "true", matchIfMissing = true)
public class PendingGovernanceEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(PendingGovernanceEventPublisher.class);
    private final RuleGovernanceEventJpaRepository repository;
    private final GovernanceDispatcher dispatcher;
    public PendingGovernanceEventPublisher(RuleGovernanceEventJpaRepository repository, GovernanceDispatcher dispatcher) {
        this.repository = repository; this.dispatcher = dispatcher;
    }
    @Scheduled(fixedDelayString = "${feedback-governance.rabbitmq.pending-publish-delay-ms:10000}")
    public void publishPending() {
        repository.findTop50ByEventStatusOrderByCreatedAtAsc(GovernanceEventService.PENDING).forEach(event -> {
            try { dispatcher.publish(event); }
            catch (Exception e) { log.warn("Failed to publish governance event {}: {}", event.getId(), e.getMessage()); }
        });
    }
}
