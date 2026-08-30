package com.example.disclosurereview.governance.persistence.entity;

import com.example.disclosurereview.governance.domain.*;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "rule_governance_memory")
public class RuleGovernanceMemoryEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Enumerated(EnumType.STRING) @Column(name = "memory_type", nullable = false, length = 32) private GovernanceMemoryType memoryType;
    @Column(name = "rule_code", length = 128) private String ruleCode;
    @Column(name = "rule_version", length = 64) private String ruleVersion;
    @Column(name = "document_category", length = 64) private String documentCategory;
    @Column(name = "declared_file_type", length = 256) private String declaredFileType;
    @Enumerated(EnumType.STRING) @Column(name = "root_cause_type", length = 64) private RootCauseType rootCauseType;
    @Enumerated(EnumType.STRING) @Column(name = "proposal_type", length = 64) private ProposalType proposalType;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "proposal_id") private RuleChangeProposalEntity proposal;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "governance_group_id") private RuleFeedbackGovernanceGroupEntity governanceGroup;
    @Enumerated(EnumType.STRING) @Column(name = "decision", nullable = false, length = 64) private GovernanceDecision decision;
    @Column(name = "decision_reason", columnDefinition = "text") private String decisionReason;
    @Column(name = "human_comment", columnDefinition = "text") private String humanComment;
    @Column(name = "case_summary", columnDefinition = "text") private String caseSummary;
    @Column(name = "agent_suggestion_summary", columnDefinition = "text") private String agentSuggestionSummary;
    @Column(name = "final_change_summary", columnDefinition = "text") private String finalChangeSummary;
    @Column(name = "before_rule_snapshot_json", columnDefinition = "text") private String beforeRuleSnapshotJson;
    @Column(name = "final_rule_snapshot_json", columnDefinition = "text") private String finalRuleSnapshotJson;
    @Column(name = "backtest_summary_json", columnDefinition = "text") private String backtestSummaryJson;
    @Column(name = "effect_summary_json", columnDefinition = "text") private String effectSummaryJson;
    @Column(name = "source_type", length = 64) private String sourceType;
    @Column(name = "source_id") private Long sourceId;
    @Column(nullable = false) private boolean enabled = true;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    public Long getId() { return id; }
    public GovernanceMemoryType getMemoryType() { return memoryType; }
    public void setMemoryType(GovernanceMemoryType v) { memoryType = v; }
    public String getRuleCode() { return ruleCode; }
    public void setRuleCode(String v) { ruleCode = v; }
    public String getRuleVersion() { return ruleVersion; }
    public void setRuleVersion(String v) { ruleVersion = v; }
    public String getDocumentCategory() { return documentCategory; }
    public void setDocumentCategory(String v) { documentCategory = v; }
    public String getDeclaredFileType() { return declaredFileType; }
    public void setDeclaredFileType(String v) { declaredFileType = v; }
    public RootCauseType getRootCauseType() { return rootCauseType; }
    public void setRootCauseType(RootCauseType v) { rootCauseType = v; }
    public ProposalType getProposalType() { return proposalType; }
    public void setProposalType(ProposalType v) { proposalType = v; }
    public RuleChangeProposalEntity getProposal() { return proposal; }
    public void setProposal(RuleChangeProposalEntity v) { proposal = v; }
    public RuleFeedbackGovernanceGroupEntity getGovernanceGroup() { return governanceGroup; }
    public void setGovernanceGroup(RuleFeedbackGovernanceGroupEntity v) { governanceGroup = v; }
    public GovernanceDecision getDecision() { return decision; }
    public void setDecision(GovernanceDecision v) { decision = v; }
    public String getDecisionReason() { return decisionReason; }
    public void setDecisionReason(String v) { decisionReason = v; }
    public String getHumanComment() { return humanComment; }
    public void setHumanComment(String v) { humanComment = v; }
    public String getCaseSummary() { return caseSummary; }
    public void setCaseSummary(String v) { caseSummary = v; }
    public String getAgentSuggestionSummary() { return agentSuggestionSummary; }
    public void setAgentSuggestionSummary(String v) { agentSuggestionSummary = v; }
    public String getFinalChangeSummary() { return finalChangeSummary; }
    public void setFinalChangeSummary(String v) { finalChangeSummary = v; }
    public String getBeforeRuleSnapshotJson() { return beforeRuleSnapshotJson; }
    public void setBeforeRuleSnapshotJson(String v) { beforeRuleSnapshotJson = v; }
    public String getFinalRuleSnapshotJson() { return finalRuleSnapshotJson; }
    public void setFinalRuleSnapshotJson(String v) { finalRuleSnapshotJson = v; }
    public String getBacktestSummaryJson() { return backtestSummaryJson; }
    public void setBacktestSummaryJson(String v) { backtestSummaryJson = v; }
    public String getEffectSummaryJson() { return effectSummaryJson; }
    public void setEffectSummaryJson(String v) { effectSummaryJson = v; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String v) { sourceType = v; }
    public Long getSourceId() { return sourceId; }
    public void setSourceId(Long v) { sourceId = v; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean v) { enabled = v; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant v) { createdAt = v; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant v) { updatedAt = v; }
}
