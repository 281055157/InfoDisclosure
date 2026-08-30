package com.example.disclosurereview.governance.persistence.entity;

import com.example.disclosurereview.governance.domain.GovernanceGroupStatus;
import com.example.disclosurereview.governance.domain.GovernanceIntent;
import com.example.disclosurereview.persistence.entity.ReviewRuleDefinitionEntity;
import com.example.disclosurereview.persistence.entity.ReviewRuleFeedbackEntity;
import com.example.disclosurereview.persistence.entity.ReviewRuleVersionEntity;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "rule_feedback_governance_group")
public class RuleFeedbackGovernanceGroupEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "group_key", nullable = false, length = 768) private String groupKey;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "rule_definition_id") private ReviewRuleDefinitionEntity ruleDefinition;
    @Column(name = "rule_code", length = 128) private String ruleCode;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "rule_version_id") private ReviewRuleVersionEntity ruleVersionEntity;
    @Column(name = "rule_version", length = 64) private String ruleVersion;
    @Enumerated(EnumType.STRING) @Column(name = "governance_intent", nullable = false, length = 32)
    private GovernanceIntent governanceIntent = GovernanceIntent.RULE_CORRECTION;
    @Column(name = "feedback_type", nullable = false, length = 64) private String feedbackType;
    @Column(name = "document_category", length = 64) private String documentCategory;
    @Column(name = "declared_file_type", length = 256) private String declaredFileType;
    @Column(name = "product_series", length = 256) private String productSeries;
    @Column(name = "issue_type", length = 128) private String issueType;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false, length = 32) private GovernanceGroupStatus status;
    @Column(name = "feedback_count", nullable = false) private int feedbackCount;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "representative_feedback_id") private ReviewRuleFeedbackEntity representativeFeedback;
    @Column(name = "latest_feedback_at") private Instant latestFeedbackAt;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "governance_run_id", nullable = false) private RuleGovernanceRunEntity governanceRun;
    @Column(name = "error_message", columnDefinition = "text") private String errorMessage;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private long version;

    public Long getId() { return id; }
    public String getGroupKey() { return groupKey; }
    public void setGroupKey(String v) { groupKey = v; }
    public ReviewRuleDefinitionEntity getRuleDefinition() { return ruleDefinition; }
    public void setRuleDefinition(ReviewRuleDefinitionEntity v) { ruleDefinition = v; }
    public String getRuleCode() { return ruleCode; }
    public void setRuleCode(String v) { ruleCode = v; }
    public ReviewRuleVersionEntity getRuleVersionEntity() { return ruleVersionEntity; }
    public void setRuleVersionEntity(ReviewRuleVersionEntity v) { ruleVersionEntity = v; }
    public String getRuleVersion() { return ruleVersion; }
    public void setRuleVersion(String v) { ruleVersion = v; }
    public GovernanceIntent getGovernanceIntent() { return governanceIntent; }
    public void setGovernanceIntent(GovernanceIntent v) { governanceIntent = v; }
    public boolean isRuleGap() { return governanceIntent == GovernanceIntent.RULE_GAP; }
    public String getFeedbackType() { return feedbackType; }
    public void setFeedbackType(String v) { feedbackType = v; }
    public String getDocumentCategory() { return documentCategory; }
    public void setDocumentCategory(String v) { documentCategory = v; }
    public String getDeclaredFileType() { return declaredFileType; }
    public void setDeclaredFileType(String v) { declaredFileType = v; }
    public String getProductSeries() { return productSeries; }
    public void setProductSeries(String v) { productSeries = v; }
    public String getIssueType() { return issueType; }
    public void setIssueType(String v) { issueType = v; }
    public GovernanceGroupStatus getStatus() { return status; }
    public void setStatus(GovernanceGroupStatus v) { status = v; }
    public int getFeedbackCount() { return feedbackCount; }
    public void setFeedbackCount(int v) { feedbackCount = v; }
    public ReviewRuleFeedbackEntity getRepresentativeFeedback() { return representativeFeedback; }
    public void setRepresentativeFeedback(ReviewRuleFeedbackEntity v) { representativeFeedback = v; }
    public Instant getLatestFeedbackAt() { return latestFeedbackAt; }
    public void setLatestFeedbackAt(Instant v) { latestFeedbackAt = v; }
    public RuleGovernanceRunEntity getGovernanceRun() { return governanceRun; }
    public void setGovernanceRun(RuleGovernanceRunEntity v) { governanceRun = v; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String v) { errorMessage = v; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant v) { createdAt = v; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant v) { updatedAt = v; }
    public long getVersion() { return version; }
}
