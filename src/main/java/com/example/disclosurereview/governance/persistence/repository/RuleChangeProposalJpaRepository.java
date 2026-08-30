package com.example.disclosurereview.governance.persistence.repository;

import com.example.disclosurereview.governance.domain.ProposalStatus;
import com.example.disclosurereview.governance.persistence.entity.RuleChangeProposalEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RuleChangeProposalJpaRepository extends JpaRepository<RuleChangeProposalEntity, Long> {
    List<RuleChangeProposalEntity> findAllByOrderByCreatedAtDesc();
    List<RuleChangeProposalEntity> findByGovernanceGroup_IdOrderByCreatedAtDesc(Long groupId);
    boolean existsByGovernanceGroup_Id(Long groupId);
    boolean existsByGovernanceGroup_IdAndProposalStatusIn(Long groupId, Collection<ProposalStatus> statuses);
    Optional<RuleChangeProposalEntity> findByDraftRuleVersion_Id(Long ruleVersionId);
    List<RuleChangeProposalEntity> findByRuleCodeAndProposalStatusInOrderByCreatedAtDesc(
            String ruleCode, Collection<ProposalStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from RuleChangeProposalEntity p where p.id = :id")
    Optional<RuleChangeProposalEntity> findLockedById(@Param("id") Long id);
}
