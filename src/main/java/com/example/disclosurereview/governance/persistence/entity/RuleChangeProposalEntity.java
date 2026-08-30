package com.example.disclosurereview.governance.persistence.entity;

import com.example.disclosurereview.governance.domain.ProposalStatus;
import com.example.disclosurereview.governance.domain.ProposalType;
import com.example.disclosurereview.governance.domain.RootCauseType;
import com.example.disclosurereview.persistence.entity.LlmModelConfigEntity;
import com.example.disclosurereview.persistence.entity.ReviewRuleDefinitionEntity;
import com.example.disclosurereview.persistence.entity.ReviewRuleVersionEntity;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "rule_change_proposal")
public class RuleChangeProposalEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "proposal_no", nullable = false, unique = true, length = 64) private String proposalNo;
    @Enumerated(EnumType.STRING) @Column(name = "proposal_type", nullable = false, length = 64) private ProposalType proposalType;
    @Enumerated(EnumType.STRING) @Column(name = "proposal_status", nullable = false, length = 64) private ProposalStatus proposalStatus;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "governance_group_id", nullable = false) private RuleFeedbackGovernanceGroupEntity governanceGroup;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "governance_run_id", nullable = false) private RuleGovernanceRunEntity governanceRun;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "rule_definition_id") private ReviewRuleDefinitionEntity ruleDefinition;
    @Column(name = "rule_code", length = 128) private String ruleCode;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "source_rule_version_id") private ReviewRuleVersionEntity sourceRuleVersionEntity;
    @Column(name = "source_rule_version", length = 64) private String sourceRuleVersion;
    @Enumerated(EnumType.STRING) @Column(name = "root_cause_type", nullable = false, length = 64) private RootCauseType rootCauseType;
    @Column(name = "agent_confidence") private Double agentConfidence;
    @Column(name = "problem_summary", columnDefinition = "text") private String problemSummary;
    @Column(name = "root_cause_analysis", columnDefinition = "text") private String rootCauseAnalysis;
    @Column(name = "change_reason", columnDefinition = "text") private String changeReason;
    @Column(name = "expected_effect", columnDefinition = "text") private String expectedEffect;
    @Column(name = "risk_description", columnDefinition = "text") private String riskDescription;
    @Column(name = "before_rule_snapshot_json", columnDefinition = "text") private String beforeRuleSnapshotJson;
    @Column(name = "after_rule_snapshot_json", columnDefinition = "text") private String afterRuleSnapshotJson;
    @Column(name = "final_rule_snapshot_json", columnDefinition = "text") private String finalRuleSnapshotJson;
    @Column(name = "change_content_json", columnDefinition = "text") private String changeContentJson;
    @Column(name = "validation_result_json", columnDefinition = "text") private String validationResultJson;
    @Column(name = "backtest_result_json", columnDefinition = "text") private String backtestResultJson;
    @Column(name = "affected_scope_json", columnDefinition = "text") private String affectedScopeJson;
    @Column(name = "optimization_category", length = 64) private String optimizationCategory;
    @Column(name = "optimization_advice", columnDefinition = "text") private String optimizationAdvice;
    @Column(name = "responsible_module", length = 128) private String responsibleModule;
    @Column(name = "proposal_priority", length = 32) private String proposalPriority;
    @Column(name = "human_follow_up_required", nullable = false) private boolean humanFollowUpRequired;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "agent_model_config_id") private LlmModelConfigEntity agentModelConfig;
    @Column(name = "agent_provider", length = 128) private String agentProvider;
    @Column(name = "agent_model", length = 256) private String agentModel;
    @Column(name = "agent_prompt_version", length = 64) private String agentPromptVersion;
    @Column(name = "agent_response_json", columnDefinition = "text") private String agentResponseJson;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "draft_rule_definition_id") private ReviewRuleDefinitionEntity draftRuleDefinition;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "draft_rule_version_id") private ReviewRuleVersionEntity draftRuleVersion;
    @Column(name = "submitted_at") private Instant submittedAt;
    @Column(name = "reviewed_at") private Instant reviewedAt;
    @Column(name = "applied_at") private Instant appliedAt;
    @Column(name = "closed_at") private Instant closedAt;
    @Column(name = "deferred_until") private Instant deferredUntil;
    @Column(name = "defer_reason", columnDefinition = "text") private String deferReason;
    @Column(name = "created_by", length = 128) private String createdBy;
    @Column(name = "reviewed_by", length = 128) private String reviewedBy;
    @Column(name = "review_comment", columnDefinition = "text") private String reviewComment;
    @Column(name = "rejection_reason", length = 64) private String rejectionReason;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private long version;

    public Long getId() { return id; }
    public String getProposalNo() { return proposalNo; }
    public void setProposalNo(String v) { proposalNo = v; }
    public ProposalType getProposalType() { return proposalType; }
    public void setProposalType(ProposalType v) { proposalType = v; }
    public ProposalStatus getProposalStatus() { return proposalStatus; }
    public void setProposalStatus(ProposalStatus v) { proposalStatus = v; }
    public RuleFeedbackGovernanceGroupEntity getGovernanceGroup() { return governanceGroup; }
    public void setGovernanceGroup(RuleFeedbackGovernanceGroupEntity v) { governanceGroup = v; }
    public RuleGovernanceRunEntity getGovernanceRun() { return governanceRun; }
    public void setGovernanceRun(RuleGovernanceRunEntity v) { governanceRun = v; }
    public ReviewRuleDefinitionEntity getRuleDefinition() { return ruleDefinition; }
    public void setRuleDefinition(ReviewRuleDefinitionEntity v) { ruleDefinition = v; }
    public String getRuleCode() { return ruleCode; }
    public void setRuleCode(String v) { ruleCode = v; }
    public ReviewRuleVersionEntity getSourceRuleVersionEntity() { return sourceRuleVersionEntity; }
    public void setSourceRuleVersionEntity(ReviewRuleVersionEntity v) { sourceRuleVersionEntity = v; }
    public String getSourceRuleVersion() { return sourceRuleVersion; }
    public void setSourceRuleVersion(String v) { sourceRuleVersion = v; }
    public RootCauseType getRootCauseType() { return rootCauseType; }
    public void setRootCauseType(RootCauseType v) { rootCauseType = v; }
    public Double getAgentConfidence() { return agentConfidence; }
    public void setAgentConfidence(Double v) { agentConfidence = v; }
    public String getProblemSummary() { return problemSummary; }
    public void setProblemSummary(String v) { problemSummary = v; }
    public String getRootCauseAnalysis() { return rootCauseAnalysis; }
    public void setRootCauseAnalysis(String v) { rootCauseAnalysis = v; }
    public String getChangeReason() { return changeReason; }
    public void setChangeReason(String v) { changeReason = v; }
    public String getExpectedEffect() { return expectedEffect; }
    public void setExpectedEffect(String v) { expectedEffect = v; }
    public String getRiskDescription() { return riskDescription; }
    public void setRiskDescription(String v) { riskDescription = v; }
    public String getBeforeRuleSnapshotJson() { return beforeRuleSnapshotJson; }
    public void setBeforeRuleSnapshotJson(String v) { beforeRuleSnapshotJson = v; }
    public String getAfterRuleSnapshotJson() { return afterRuleSnapshotJson; }
    public void setAfterRuleSnapshotJson(String v) { afterRuleSnapshotJson = v; }
    public String getFinalRuleSnapshotJson() { return finalRuleSnapshotJson; }
    public void setFinalRuleSnapshotJson(String v) { finalRuleSnapshotJson = v; }
    public String getChangeContentJson() { return changeContentJson; }
    public void setChangeContentJson(String v) { changeContentJson = v; }
    public String getValidationResultJson() { return validationResultJson; }
    public void setValidationResultJson(String v) { validationResultJson = v; }
    public String getBacktestResultJson() { return backtestResultJson; }
    public void setBacktestResultJson(String v) { backtestResultJson = v; }
    public String getAffectedScopeJson() { return affectedScopeJson; }
    public void setAffectedScopeJson(String v) { affectedScopeJson = v; }
    public String getOptimizationCategory() { return optimizationCategory; }
    public void setOptimizationCategory(String v) { optimizationCategory = v; }
    public String getOptimizationAdvice() { return optimizationAdvice; }
    public void setOptimizationAdvice(String v) { optimizationAdvice = v; }
    public String getResponsibleModule() { return responsibleModule; }
    public void setResponsibleModule(String v) { responsibleModule = v; }
    public String getProposalPriority() { return proposalPriority; }
    public void setProposalPriority(String v) { proposalPriority = v; }
    public boolean isHumanFollowUpRequired() { return humanFollowUpRequired; }
    public void setHumanFollowUpRequired(boolean v) { humanFollowUpRequired = v; }
    public LlmModelConfigEntity getAgentModelConfig() { return agentModelConfig; }
    public void setAgentModelConfig(LlmModelConfigEntity v) { agentModelConfig = v; }
    public String getAgentProvider() { return agentProvider; }
    public void setAgentProvider(String v) { agentProvider = v; }
    public String getAgentModel() { return agentModel; }
    public void setAgentModel(String v) { agentModel = v; }
    public String getAgentPromptVersion() { return agentPromptVersion; }
    public void setAgentPromptVersion(String v) { agentPromptVersion = v; }
    public String getAgentResponseJson() { return agentResponseJson; }
    public void setAgentResponseJson(String v) { agentResponseJson = v; }
    public ReviewRuleDefinitionEntity getDraftRuleDefinition() { return draftRuleDefinition; }
    public void setDraftRuleDefinition(ReviewRuleDefinitionEntity v) { draftRuleDefinition = v; }
    public ReviewRuleVersionEntity getDraftRuleVersion() { return draftRuleVersion; }
    public void setDraftRuleVersion(ReviewRuleVersionEntity v) { draftRuleVersion = v; }
    public Instant getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Instant v) { submittedAt = v; }
    public Instant getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Instant v) { reviewedAt = v; }
    public Instant getAppliedAt() { return appliedAt; }
    public void setAppliedAt(Instant v) { appliedAt = v; }
    public Instant getClosedAt() { return closedAt; }
    public void setClosedAt(Instant v) { closedAt = v; }
    public Instant getDeferredUntil() { return deferredUntil; }
    public void setDeferredUntil(Instant v) { deferredUntil = v; }
    public String getDeferReason() { return deferReason; }
    public void setDeferReason(String v) { deferReason = v; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String v) { createdBy = v; }
    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String v) { reviewedBy = v; }
    public String getReviewComment() { return reviewComment; }
    public void setReviewComment(String v) { reviewComment = v; }
    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String v) { rejectionReason = v; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant v) { createdAt = v; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant v) { updatedAt = v; }
    public long getVersion() { return version; }
}
