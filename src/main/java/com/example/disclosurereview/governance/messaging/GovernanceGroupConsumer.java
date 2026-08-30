package com.example.disclosurereview.governance.messaging;

import com.example.disclosurereview.config.FeedbackGovernanceProperties;
import com.example.disclosurereview.governance.agent.GovernanceAgentOrchestrator;
import com.example.disclosurereview.governance.domain.GovernanceGroupStatus;
import com.example.disclosurereview.governance.persistence.entity.RuleGovernanceEventEntity;
import com.example.disclosurereview.governance.persistence.repository.RuleFeedbackGovernanceGroupJpaRepository;
import com.example.disclosurereview.governance.service.GovernanceRunProgressService;
import com.example.disclosurereview.governance.service.GovernanceTraceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

@Service
public class GovernanceGroupConsumer {
    private static final Logger log = LoggerFactory.getLogger(GovernanceGroupConsumer.class);
    private final GovernanceEventService eventService;
    private final GovernanceDispatcher dispatcher;
    private final GovernanceAgentOrchestrator orchestrator;
    private final GovernanceRunProgressService progressService;
    private final RuleFeedbackGovernanceGroupJpaRepository groupRepository;
    private final FeedbackGovernanceProperties properties;
    private final GovernanceTraceService traceService;

    public GovernanceGroupConsumer(GovernanceEventService eventService,
                                   GovernanceDispatcher dispatcher,
                                   GovernanceAgentOrchestrator orchestrator,
                                   GovernanceRunProgressService progressService,
                                   RuleFeedbackGovernanceGroupJpaRepository groupRepository,
                                   FeedbackGovernanceProperties properties,
                                   GovernanceTraceService traceService) {
        this.eventService = eventService; this.dispatcher = dispatcher; this.orchestrator = orchestrator;
        this.progressService = progressService; this.groupRepository = groupRepository; this.properties = properties;
        this.traceService = traceService;
    }

    @RabbitListener(queues = "${feedback-governance.rabbitmq.queue:feedback.governance.group.analyze}",
            concurrency = "${feedback-governance.rabbitmq.listener-concurrency:1-4}")
    public void consume(GovernanceGroupMessage message) {
        RuleGovernanceEventEntity event;
        try { event = eventService.get(message.eventId()); }
        catch (Exception missing) { log.warn("Ignore missing governance event {}", message.eventId()); return; }
        if (GovernanceEventService.COMPLETED.equals(event.getEventStatus()) || GovernanceEventService.FAILED.equals(event.getEventStatus())) return;
        var group = groupRepository.findById(message.groupId()).orElse(null);
        if (group == null) { eventService.fail(event.getId(), "治理分组不存在"); return; }
        if (group.getStatus() == GovernanceGroupStatus.PROPOSAL_CREATED || group.getStatus() == GovernanceGroupStatus.RESOLVED) {
            eventService.complete(event.getId()); progressService.refresh(message.governanceRunId()); return;
        }
        if (!eventService.tryMarkProcessing(event.getId())) return;
        GovernanceTraceService.SpanScope messageSpan = traceService.open(message.governanceRunId(), message.groupId(),
                "MESSAGE_CONSUMER", "RabbitMQ 分组分析 #" + message.groupId(), "PARALLEL",
                "governance-run-" + message.governanceRunId() + "-groups", message.attempt(), message.attempt(),
                null, null, java.util.Map.of("eventId", message.eventId(), "deliveryAttempt", message.attempt()));
        try {
            orchestrator.analyze(message.governanceRunId(), message.groupId());
            eventService.complete(event.getId());
            messageSpan.success();
        } catch (Exception e) {
            String error = safe(e);
            messageSpan.fail(e);
            if (message.attempt() < properties.getRabbitmq().getMaximumAttempts()) {
                RuleGovernanceEventEntity retry = eventService.pendingRetry(event.getId(), error);
                try { dispatcher.publish(retry); }
                catch (Exception publishFailure) { log.warn("Governance retry event {} remains pending: {}", retry.getId(), publishFailure.getMessage()); }
            } else {
                eventService.fail(event.getId(), error);
            }
        } finally {
            messageSpan.close();
            progressService.refresh(message.governanceRunId());
        }
    }

    private String safe(Throwable error) {
        String value = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        return value.length() > 2000 ? value.substring(0, 2000) : value;
    }
}
