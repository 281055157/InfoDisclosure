package com.example.disclosurereview.governance.persistence.repository;

import com.example.disclosurereview.governance.persistence.entity.RuleGovernanceMemoryEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RuleGovernanceMemoryJpaRepository extends JpaRepository<RuleGovernanceMemoryEntity, Long> {
    List<RuleGovernanceMemoryEntity> findAllByOrderByCreatedAtDesc();
    List<RuleGovernanceMemoryEntity> findByProposal_IdOrderByCreatedAtDesc(Long proposalId);

    @Query("select m from RuleGovernanceMemoryEntity m where m.enabled = true " +
            "and (:ruleCode is null or m.ruleCode = :ruleCode) " +
            "and (:documentCategory is null or m.documentCategory = :documentCategory) " +
            "and (:declaredFileType is null or m.declaredFileType = :declaredFileType) " +
            "and (:rootCause is null or str(m.rootCauseType) = :rootCause) order by m.createdAt desc")
    List<RuleGovernanceMemoryEntity> search(@Param("ruleCode") String ruleCode,
                                            @Param("documentCategory") String documentCategory,
                                            @Param("declaredFileType") String declaredFileType,
                                            @Param("rootCause") String rootCause,
                                            Pageable pageable);
}
