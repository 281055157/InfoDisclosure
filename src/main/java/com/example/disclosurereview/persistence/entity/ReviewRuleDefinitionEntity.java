package com.example.disclosurereview.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "review_rule_definition")
public class ReviewRuleDefinitionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rule_code", nullable = false, unique = true, length = 128)
    private String ruleCode;

    @Column(name = "rule_name", nullable = false, length = 256)
    private String ruleName;

    @Column(name = "rule_type", nullable = false, length = 128)
    private String ruleType;

    @Column(name = "rule_category", length = 64)
    private String ruleCategory;

    @Column(name = "active_version_id")
    private Long activeVersionId;

    @Column(name = "priority", nullable = false)
    private int priority = 100;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "severity", length = 64)
    private String severity;

    @Column(name = "confidence")
    private Double confidence;

    @Column(name = "document_types", columnDefinition = "text")
    private String documentTypes;

    @Column(name = "product_scope", columnDefinition = "text")
    private String productScope;

    @Column(name = "parameters_json", columnDefinition = "text")
    private String parametersJson;

    @Column(name = "version_code", nullable = false, length = 64)
    private String versionCode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by", length = 128)
    private String createdBy;

    @Column(name = "updated_by", length = 128)
    private String updatedBy;

    public Long getId() {
        return id;
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public void setRuleCode(String ruleCode) {
        this.ruleCode = ruleCode;
    }

    public String getRuleName() {
        return ruleName;
    }

    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    public String getRuleType() {
        return ruleType;
    }

    public void setRuleType(String ruleType) {
        this.ruleType = ruleType;
    }

    public String getRuleCategory() {
        return ruleCategory;
    }

    public void setRuleCategory(String ruleCategory) {
        this.ruleCategory = ruleCategory;
    }

    public Long getActiveVersionId() {
        return activeVersionId;
    }

    public void setActiveVersionId(Long activeVersionId) {
        this.activeVersionId = activeVersionId;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public String getDocumentTypes() {
        return documentTypes;
    }

    public void setDocumentTypes(String documentTypes) {
        this.documentTypes = documentTypes;
    }

    public String getProductScope() {
        return productScope;
    }

    public void setProductScope(String productScope) {
        this.productScope = productScope;
    }

    public String getParametersJson() {
        return parametersJson;
    }

    public void setParametersJson(String parametersJson) {
        this.parametersJson = parametersJson;
    }

    public String getVersionCode() {
        return versionCode;
    }

    public void setVersionCode(String versionCode) {
        this.versionCode = versionCode;
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

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }
}
