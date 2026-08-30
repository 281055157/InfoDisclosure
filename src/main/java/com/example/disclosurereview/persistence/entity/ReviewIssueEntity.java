package com.example.disclosurereview.persistence.entity;

import com.example.disclosurereview.model.ReviewIssueStatus;
import com.example.disclosurereview.model.Severity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "review_issue")
public class ReviewIssueEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private ReviewTaskEntity task;

    @Column(name = "issue_code", nullable = false, length = 128)
    private String issueCode;

    @Column(name = "issue_name", length = 256)
    private String issueName;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", length = 64)
    private Severity severity;

    @Column(name = "confidence")
    private Double confidence;

    @Column(name = "page_number")
    private Integer pageNumber;

    @Column(name = "sheet_name", length = 256)
    private String sheetName;

    @Column(name = "cell_address", length = 64)
    private String cellAddress;

    @Column(name = "evidence_text", columnDefinition = "text")
    private String evidenceText;

    @Column(name = "evidence_verified", nullable = false)
    private boolean evidenceVerified;

    @Column(name = "explanation", columnDefinition = "text")
    private String explanation;

    @Column(name = "suggestion", columnDefinition = "text")
    private String suggestion;

    @Column(name = "source_type", length = 64)
    private String sourceType;

    @Column(name = "rule_code", length = 128)
    private String ruleCode;

    @Column(name = "rule_version_id")
    private Long ruleVersionId;

    @Column(name = "rule_execution_id")
    private Long ruleExecutionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "model_call_id")
    private ModelCallRecordEntity modelCall;

    @Enumerated(EnumType.STRING)
    @Column(name = "issue_status", nullable = false, length = 64)
    private ReviewIssueStatus issueStatus;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public ReviewTaskEntity getTask() {
        return task;
    }

    public void setTask(ReviewTaskEntity task) {
        this.task = task;
    }

    public String getIssueCode() {
        return issueCode;
    }

    public void setIssueCode(String issueCode) {
        this.issueCode = issueCode;
    }

    public String getIssueName() {
        return issueName;
    }

    public void setIssueName(String issueName) {
        this.issueName = issueName;
    }

    public Severity getSeverity() {
        return severity;
    }

    public void setSeverity(Severity severity) {
        this.severity = severity;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public Integer getPageNumber() {
        return pageNumber;
    }

    public void setPageNumber(Integer pageNumber) {
        this.pageNumber = pageNumber;
    }

    public String getSheetName() {
        return sheetName;
    }

    public void setSheetName(String sheetName) {
        this.sheetName = sheetName;
    }

    public String getCellAddress() {
        return cellAddress;
    }

    public void setCellAddress(String cellAddress) {
        this.cellAddress = cellAddress;
    }

    public String getEvidenceText() {
        return evidenceText;
    }

    public void setEvidenceText(String evidenceText) {
        this.evidenceText = evidenceText;
    }

    public boolean isEvidenceVerified() {
        return evidenceVerified;
    }

    public void setEvidenceVerified(boolean evidenceVerified) {
        this.evidenceVerified = evidenceVerified;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public String getSuggestion() {
        return suggestion;
    }

    public void setSuggestion(String suggestion) {
        this.suggestion = suggestion;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public void setRuleCode(String ruleCode) {
        this.ruleCode = ruleCode;
    }

    public Long getRuleVersionId() {
        return ruleVersionId;
    }

    public void setRuleVersionId(Long ruleVersionId) {
        this.ruleVersionId = ruleVersionId;
    }

    public Long getRuleExecutionId() {
        return ruleExecutionId;
    }

    public void setRuleExecutionId(Long ruleExecutionId) {
        this.ruleExecutionId = ruleExecutionId;
    }

    public ModelCallRecordEntity getModelCall() {
        return modelCall;
    }

    public void setModelCall(ModelCallRecordEntity modelCall) {
        this.modelCall = modelCall;
    }

    public ReviewIssueStatus getIssueStatus() {
        return issueStatus;
    }

    public void setIssueStatus(ReviewIssueStatus issueStatus) {
        this.issueStatus = issueStatus;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
