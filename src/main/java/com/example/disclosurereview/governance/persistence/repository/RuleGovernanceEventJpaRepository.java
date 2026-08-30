package com.example.disclosurereview.governance.persistence.repository;

import com.example.disclosurereview.governance.persistence.entity.RuleGovernanceEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RuleGovernanceEventJpaRepository extends JpaRepository<RuleGovernanceEventEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RuleGovernanceEventEntity> findLockedById(Long id);
    List<RuleGovernanceEventEntity> findTop50ByEventStatusOrderByCreatedAtAsc(String status);
    List<RuleGovernanceEventEntity> findByGovernanceGroup_IdAndEventTypeAndEventStatusInOrderByCreatedAtAsc(
            Long groupId, String eventType, Collection<String> statuses);
    List<RuleGovernanceEventEntity> findByGovernanceRun_IdOrderByCreatedAtAsc(Long runId);
}
