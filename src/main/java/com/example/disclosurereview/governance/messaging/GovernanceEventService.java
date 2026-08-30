package com.example.disclosurereview.governance.messaging;

import com.example.disclosurereview.governance.persistence.entity.*;
import com.example.disclosurereview.governance.persistence.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class GovernanceEventService {
    public static final String PENDING = "PENDING";
    public static final String PUBLISHED = "PUBLISHED";
    public static final String PROCESSING = "PROCESSING";
    public static final String COMPLETED = "COMPLETED";
    public static final String FAILED = "FAILED";
    public static final String ANALYZE_GROUP = "ANALYZE_GROUP";
    private static final List<String> ACTIVE = List.of(PENDING, PUBLISHED, PROCESSING);

    private final RuleGovernanceEventJpaRepository repository;
    private final RuleGovernanceRunJpaRepository runRepository;
    private final RuleFeedbackGovernanceGroupJpaRepository groupRepository;

    public GovernanceEventService(RuleGovernanceEventJpaRepository repository,
                                  RuleGovernanceRunJpaRepository runRepository,
                                  RuleFeedbackGovernanceGroupJpaRepository groupRepository) {
        this.repository = repository; this.runRepository = runRepository; this.groupRepository = groupRepository;
    }

    @Transactional
    public EventLease createOrReuse(Long runId, Long groupId) {
        var active = repository.findByGovernanceGroup_IdAndEventTypeAndEventStatusInOrderByCreatedAtAsc(
                groupId, ANALYZE_GROUP, ACTIVE);
        if (!active.isEmpty()) return new EventLease(active.get(0), false);
        RuleGovernanceEventEntity event = new RuleGovernanceEventEntity();
        event.setGovernanceRun(runRepository.findById(runId).orElseThrow());
        event.setGovernanceGroup(groupRepository.findById(groupId).orElseThrow());
        event.setEventType(ANALYZE_GROUP); event.setEventStatus(PENDING); event.setPayloadJson("{}");
        event.setRetryCount(0); event.setCreatedAt(Instant.now());
        return new EventLease(repository.save(event), true);
    }

    @Transactional public RuleGovernanceEventEntity markPublished(Long id) { RuleGovernanceEventEntity e = get(id); e.setEventStatus(PUBLISHED); e.setPublishedAt(Instant.now()); return repository.save(e); }
    @Transactional
    public boolean tryMarkProcessing(Long id) {
        RuleGovernanceEventEntity event = repository.findLockedById(id)
                .orElseThrow(() -> new IllegalArgumentException("治理事件不存在: " + id));
        if (COMPLETED.equals(event.getEventStatus()) || FAILED.equals(event.getEventStatus())
                || PROCESSING.equals(event.getEventStatus())) return false;
        var active = repository.findByGovernanceGroup_IdAndEventTypeAndEventStatusInOrderByCreatedAtAsc(
                event.getGovernanceGroup().getId(), event.getEventType(), ACTIVE);
        if (!active.isEmpty() && !active.get(0).getId().equals(id)) return false;
        event.setEventStatus(PROCESSING);
        repository.save(event);
        return true;
    }
    @Transactional public void complete(Long id) { RuleGovernanceEventEntity e = get(id); e.setEventStatus(COMPLETED); e.setCompletedAt(Instant.now()); repository.save(e); }
    @Transactional public RuleGovernanceEventEntity pendingRetry(Long id, String error) { RuleGovernanceEventEntity e = get(id); e.setRetryCount(e.getRetryCount() + 1); e.setErrorMessage(error); e.setEventStatus(PENDING); return repository.save(e); }
    @Transactional public void fail(Long id, String error) { RuleGovernanceEventEntity e = get(id); e.setEventStatus(FAILED); e.setErrorMessage(error); e.setCompletedAt(Instant.now()); repository.save(e); }
    @Transactional(readOnly = true) public RuleGovernanceEventEntity get(Long id) { return repository.findById(id).orElseThrow(() -> new IllegalArgumentException("治理事件不存在: " + id)); }
    public record EventLease(RuleGovernanceEventEntity event, boolean newlyCreated) {}
}
