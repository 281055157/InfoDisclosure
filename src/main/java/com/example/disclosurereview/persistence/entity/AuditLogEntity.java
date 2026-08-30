package com.example.disclosurereview.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "audit_log")
public class AuditLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id")
    private ReviewTaskEntity task;

    @Column(name = "operation_type", nullable = false, length = 128)
    private String operationType;

    @Column(name = "operator", length = 128)
    private String operator;

    @Column(name = "operation_detail", columnDefinition = "text")
    private String operationDetail;

    @Column(name = "before_value", columnDefinition = "text")
    private String beforeValue;

    @Column(name = "after_value", columnDefinition = "text")
    private String afterValue;

    @Column(name = "trace_id", length = 128)
    private String traceId;

    @Column(name = "governance_run_id")
    private Long governanceRunId;

    @Column(name = "governance_group_id")
    private Long governanceGroupId;

    @Column(name = "proposal_id")
    private Long proposalId;

    @Column(name = "rule_code", length = 128)
    private String ruleCode;

    @Column(name = "source_feedback_ids", columnDefinition = "text")
    private String sourceFeedbackIds;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public ReviewTaskEntity getTask() {
        return task;
    }

    public void setTask(ReviewTaskEntity task) {
        this.task = task;
    }

    public String getOperationType() {
        return operationType;
    }

    public void setOperationType(String operationType) {
        this.operationType = operationType;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public String getOperationDetail() {
        return operationDetail;
    }

    public void setOperationDetail(String operationDetail) {
        this.operationDetail = operationDetail;
    }

    public String getBeforeValue() {
        return beforeValue;
    }

    public void setBeforeValue(String beforeValue) {
        this.beforeValue = beforeValue;
    }

    public String getAfterValue() {
        return afterValue;
    }

    public void setAfterValue(String afterValue) {
        this.afterValue = afterValue;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public Long getGovernanceRunId() { return governanceRunId; }
    public void setGovernanceRunId(Long value) { governanceRunId = value; }
    public Long getGovernanceGroupId() { return governanceGroupId; }
    public void setGovernanceGroupId(Long value) { governanceGroupId = value; }
    public Long getProposalId() { return proposalId; }
    public void setProposalId(Long value) { proposalId = value; }
    public String getRuleCode() { return ruleCode; }
    public void setRuleCode(String value) { ruleCode = value; }
    public String getSourceFeedbackIds() { return sourceFeedbackIds; }
    public void setSourceFeedbackIds(String value) { sourceFeedbackIds = value; }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
