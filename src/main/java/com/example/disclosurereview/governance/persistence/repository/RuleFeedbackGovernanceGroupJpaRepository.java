package com.example.disclosurereview.governance.persistence.repository;

import com.example.disclosurereview.governance.domain.GovernanceGroupStatus;
import com.example.disclosurereview.governance.persistence.entity.RuleFeedbackGovernanceGroupEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RuleFeedbackGovernanceGroupJpaRepository extends JpaRepository<RuleFeedbackGovernanceGroupEntity, Long> {
    List<RuleFeedbackGovernanceGroupEntity> findAllByOrderByCreatedAtDesc();
    List<RuleFeedbackGovernanceGroupEntity> findByGovernanceRun_IdOrderById(Long runId);

    long countByGovernanceRun_Id(Long runId);
    Optional<RuleFeedbackGovernanceGroupEntity> findFirstByGroupKeyAndStatusInOrderByCreatedAtDesc(
            String groupKey, Collection<GovernanceGroupStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from RuleFeedbackGovernanceGroupEntity g where g.id = :id")
    Optional<RuleFeedbackGovernanceGroupEntity> findLockedById(@Param("id") Long id);
}
