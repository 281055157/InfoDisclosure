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
@Table(name = "review_rule_feedback")
public class ReviewRuleFeedbackEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private ReviewTaskEntity task;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "issue_id")
    private ReviewIssueEntity issue;

    @Column(name = "rule_code", length = 128)
    private String ruleCode;

    @Column(name = "rule_version_id")
    private Long ruleVersionId;

    @Column(name = "rule_execution_id")
    private Long ruleExecutionId;

    @Column(name = "feedback_type", nullable = false, length = 64)
    private String feedbackType;

    @Column(name = "document_category", length = 64)
    private String documentCategory;

    @Column(name = "declared_product_code", length = 128)
    private String declaredProductCode;

    @Column(name = "declared_document_type", length = 256)
    private String declaredDocumentType;

    @Column(name = "feedback_source", length = 64)
    private String feedbackSource;

    @Column(name = "feedback_tags", columnDefinition = "text")
    private String feedbackTags;

    @Column(name = "aggregation_key", length = 512)
    private String aggregationKey;

    @Column(name = "process_status", nullable = false, length = 64)
    private String processStatus;

    @Column(name = "issue_snapshot_json", columnDefinition = "text")
    private String issueSnapshotJson;

    @Column(name = "manual_snapshot_json", columnDefinition = "text")
    private String manualSnapshotJson;

    @Column(name = "comment", columnDefinition = "text")
    private String comment;

    @Column(name = "reviewer", length = 128)
    private String reviewer;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    public Long getId() {
        return id;
    }

    public ReviewTaskEntity getTask() {
        return task;
    }

    public ReviewIssueEntity getIssue() {
        return issue;
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public Long getRuleVersionId() {
        return ruleVersionId;
    }

    public Long getRuleExecutionId() {
        return ruleExecutionId;
    }

    public String getFeedbackType() {
        return feedbackType;
    }

    public String getDocumentCategory() {
        return documentCategory;
    }

    public String getDeclaredProductCode() {
        return declaredProductCode;
    }

    public String getDeclaredDocumentType() {
        return declaredDocumentType;
    }

    public String getFeedbackSource() {
        return feedbackSource;
    }

    public String getFeedbackTags() {
        return feedbackTags;
    }

    public String getAggregationKey() {
        return aggregationKey;
    }

    public String getProcessStatus() {
        return processStatus;
    }

    public String getIssueSnapshotJson() {
        return issueSnapshotJson;
    }

    public String getManualSnapshotJson() {
        return manualSnapshotJson;
    }

    public String getComment() {
        return comment;
    }

    public String getReviewer() {
        return reviewer;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public void setTask(ReviewTaskEntity task) {
        this.task = task;
    }

    public void setIssue(ReviewIssueEntity issue) {
        this.issue = issue;
    }

    public void setRuleCode(String ruleCode) {
        this.ruleCode = ruleCode;
    }

    public void setRuleVersionId(Long ruleVersionId) {
        this.ruleVersionId = ruleVersionId;
    }

    public void setRuleExecutionId(Long ruleExecutionId) {
        this.ruleExecutionId = ruleExecutionId;
    }

    public void setFeedbackType(String feedbackType) {
        this.feedbackType = feedbackType;
    }

    public void setDocumentCategory(String documentCategory) {
        this.documentCategory = documentCategory;
    }

    public void setDeclaredProductCode(String declaredProductCode) {
        this.declaredProductCode = declaredProductCode;
    }

    public void setDeclaredDocumentType(String declaredDocumentType) {
        this.declaredDocumentType = declaredDocumentType;
    }

    public void setFeedbackSource(String feedbackSource) {
        this.feedbackSource = feedbackSource;
    }

    public void setFeedbackTags(String feedbackTags) {
        this.feedbackTags = feedbackTags;
    }

    public void setAggregationKey(String aggregationKey) {
        this.aggregationKey = aggregationKey;
    }

    public void setProcessStatus(String processStatus) {
        this.processStatus = processStatus;
    }

    public void setIssueSnapshotJson(String issueSnapshotJson) {
        this.issueSnapshotJson = issueSnapshotJson;
    }

    public void setManualSnapshotJson(String manualSnapshotJson) {
        this.manualSnapshotJson = manualSnapshotJson;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public void setReviewer(String reviewer) {
        this.reviewer = reviewer;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public void setProcessedAt(Instant processedAt) {
        this.processedAt = processedAt;
    }
}
