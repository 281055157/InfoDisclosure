package com.example.disclosurereview.persistence.repository;

import com.example.disclosurereview.persistence.entity.AuditLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogJpaRepository extends JpaRepository<AuditLogEntity, Long> {

    List<AuditLogEntity> findByTaskIdOrderByCreatedAtAsc(Long taskId);

    List<AuditLogEntity> findByProposalIdOrderByCreatedAtAsc(Long proposalId);
}
