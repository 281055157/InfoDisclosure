package com.example.disclosurereview.governance.messaging;

import com.example.disclosurereview.config.FeedbackGovernanceProperties;
import com.example.disclosurereview.governance.persistence.entity.RuleGovernanceEventEntity;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class GovernanceDispatcher {
    private static final Logger log = LoggerFactory.getLogger(GovernanceDispatcher.class);
    private final GovernanceEventService eventService;
    private final RabbitTemplate rabbitTemplate;
    private final FeedbackGovernanceProperties properties;

    public GovernanceDispatcher(GovernanceEventService eventService, RabbitTemplate rabbitTemplate,
                                FeedbackGovernanceProperties properties) {
        this.eventService = eventService; this.rabbitTemplate = rabbitTemplate; this.properties = properties;
    }

    public boolean dispatch(Long runId, Long groupId) {
        GovernanceEventService.EventLease lease = eventService.createOrReuse(runId, groupId);
        if (lease.newlyCreated()) afterCommitOrNow(() -> {
            try { publish(lease.event()); }
            catch (Exception e) { log.warn("Governance event {} remains pending: {}", lease.event().getId(), e.getMessage()); }
        });
        return lease.newlyCreated();
    }

    public void publish(RuleGovernanceEventEntity event) {
        rabbitTemplate.convertAndSend(properties.getRabbitmq().getExchange(), properties.getRabbitmq().getRoutingKey(),
                new GovernanceGroupMessage(event.getId(), event.getGovernanceRun().getId(),
                        event.getGovernanceGroup().getId(), event.getRetryCount() + 1));
        eventService.markPublished(event.getId());
    }

    private void afterCommitOrNow(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) { action.run(); return; }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { action.run(); }
        });
    }
}
