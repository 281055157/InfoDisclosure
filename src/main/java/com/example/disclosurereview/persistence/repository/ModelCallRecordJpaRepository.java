package com.example.disclosurereview.persistence.repository;

import com.example.disclosurereview.persistence.entity.ModelCallRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ModelCallRecordJpaRepository extends JpaRepository<ModelCallRecordEntity, Long> {

    List<ModelCallRecordEntity> findByTaskIdOrderById(Long taskId);

    List<ModelCallRecordEntity> findByGovernanceRunIdAndGovernanceGroupIdOrderById(Long runId, Long groupId);

    List<ModelCallRecordEntity> findByGovernanceRunIdOrderById(Long runId);

    @Query("""
            select coalesce(sum(coalesce(m.inputTokenCount, 0)), 0),
                   coalesce(sum(coalesce(m.outputTokenCount, 0)), 0),
                   coalesce(sum(coalesce(m.cacheHitTokenCount, 0)), 0),
                   count(m)
            from ModelCallRecordEntity m
            where m.task.id = :taskId
            """)
    Object[] usageSummary(@Param("taskId") Long taskId);
}
