package com.example.disclosurereview.service;

import com.example.disclosurereview.config.TraceIdFilter;
import com.example.disclosurereview.persistence.entity.AuditLogEntity;
import com.example.disclosurereview.persistence.entity.ReviewTaskEntity;
import com.example.disclosurereview.persistence.repository.AuditLogJpaRepository;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class AuditLogService {

    private final AuditLogJpaRepository auditLogRepository;

    public AuditLogService(AuditLogJpaRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void record(ReviewTaskEntity task,
                       String operationType,
                       String operator,
                       String detail,
                       String beforeValue,
                       String afterValue) {
        AuditLogEntity log = new AuditLogEntity();
        log.setTask(task);
        log.setOperationType(operationType);
        log.setOperator(operator == null || operator.isBlank() ? "SYSTEM" : operator);
        log.setOperationDetail(detail);
        log.setBeforeValue(beforeValue);
        log.setAfterValue(afterValue);
        log.setTraceId(MDC.get(TraceIdFilter.TRACE_ID));
        log.setCreatedAt(Instant.now());
        auditLogRepository.save(log);
    }

    public List<AuditLogEntity> timeline(Long taskId) {
        return auditLogRepository.findByTaskIdOrderByCreatedAtAsc(taskId);
    }

    public void recordGovernance(Long runId,
                                 Long groupId,
                                 Long proposalId,
                                 String ruleCode,
                                 String sourceFeedbackIds,
                                 String operationType,
                                 String operator,
                                 String detail,
                                 String beforeValue,
                                 String afterValue) {
        AuditLogEntity log = new AuditLogEntity();
        log.setGovernanceRunId(runId);
        log.setGovernanceGroupId(groupId);
        log.setProposalId(proposalId);
        log.setRuleCode(ruleCode);
        log.setSourceFeedbackIds(sourceFeedbackIds);
        log.setOperationType(operationType);
        log.setOperator(operator == null || operator.isBlank() ? "SYSTEM" : operator);
        log.setOperationDetail(detail);
        log.setBeforeValue(beforeValue);
        log.setAfterValue(afterValue);
        log.setTraceId(MDC.get(TraceIdFilter.TRACE_ID));
        log.setCreatedAt(Instant.now());
        auditLogRepository.save(log);
    }

    public List<AuditLogEntity> proposalTimeline(Long proposalId) {
        return auditLogRepository.findByProposalIdOrderByCreatedAtAsc(proposalId);
    }
}
