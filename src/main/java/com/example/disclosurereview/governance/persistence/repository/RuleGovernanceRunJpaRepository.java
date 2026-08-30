package com.example.disclosurereview.governance.persistence.repository;

import com.example.disclosurereview.governance.persistence.entity.RuleGovernanceRunEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RuleGovernanceRunJpaRepository extends JpaRepository<RuleGovernanceRunEntity, Long> {
    List<RuleGovernanceRunEntity> findAllByOrderByCreatedAtDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RuleGovernanceRunEntity r where r.id = :id")
    Optional<RuleGovernanceRunEntity> findLockedById(@Param("id") Long id);
}
