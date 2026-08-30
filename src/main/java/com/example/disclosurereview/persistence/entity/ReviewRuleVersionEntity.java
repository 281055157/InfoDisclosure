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
@Table(name = "review_rule_version")
public class ReviewRuleVersionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "version_code", nullable = false, length = 64)
    private String versionCode;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_definition_id")
    private ReviewRuleDefinitionEntity ruleDefinition;

    @Column(name = "version_number")
    private Integer versionNumber;

    @Column(name = "executor_type", length = 64)
    private String executorType;

    @Column(name = "scope_json", columnDefinition = "text")
    private String scopeJson;

    @Column(name = "condition_json", columnDefinition = "text")
    private String conditionJson;

    @Column(name = "action_json", columnDefinition = "text")
    private String actionJson;

    @Column(name = "prompt_json", columnDefinition = "text")
    private String promptJson;

    @Column(name = "status", length = 64)
    private String status;

    @Column(name = "change_summary", columnDefinition = "text")
    private String changeSummary;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "source_proposal_id")
    private Long sourceProposalId;

    public Long getId() {
        return id;
    }

    public String getVersionCode() {
        return versionCode;
    }

    public void setVersionCode(String versionCode) {
        this.versionCode = versionCode;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
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

    public ReviewRuleDefinitionEntity getRuleDefinition() {
        return ruleDefinition;
    }

    public void setRuleDefinition(ReviewRuleDefinitionEntity ruleDefinition) {
        this.ruleDefinition = ruleDefinition;
    }

    public Integer getVersionNumber() {
        return versionNumber;
    }

    public void setVersionNumber(Integer versionNumber) {
        this.versionNumber = versionNumber;
    }

    public String getExecutorType() {
        return executorType;
    }

    public void setExecutorType(String executorType) {
        this.executorType = executorType;
    }

    public String getScopeJson() {
        return scopeJson;
    }

    public void setScopeJson(String scopeJson) {
        this.scopeJson = scopeJson;
    }

    public String getConditionJson() {
        return conditionJson;
    }

    public void setConditionJson(String conditionJson) {
        this.conditionJson = conditionJson;
    }

    public String getActionJson() {
        return actionJson;
    }

    public void setActionJson(String actionJson) {
        this.actionJson = actionJson;
    }

    public String getPromptJson() {
        return promptJson;
    }

    public void setPromptJson(String promptJson) {
        this.promptJson = promptJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getChangeSummary() {
        return changeSummary;
    }

    public void setChangeSummary(String changeSummary) {
        this.changeSummary = changeSummary;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public Long getSourceProposalId() { return sourceProposalId; }
    public void setSourceProposalId(Long value) { sourceProposalId = value; }
}
