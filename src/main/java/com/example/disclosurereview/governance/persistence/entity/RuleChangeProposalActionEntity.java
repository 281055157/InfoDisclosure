package com.example.disclosurereview.governance.persistence.entity;

import com.example.disclosurereview.governance.domain.ProposalActionStatus;
import com.example.disclosurereview.governance.domain.ProposalType;
import com.example.disclosurereview.persistence.entity.ReviewRuleDefinitionEntity;
import com.example.disclosurereview.persistence.entity.ReviewRuleVersionEntity;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "rule_change_proposal_action")
public class RuleChangeProposalActionEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "proposal_id", nullable = false) private RuleChangeProposalEntity proposal;
    @Column(name = "sequence_no", nullable = false) private int sequenceNo;
    @Enumerated(EnumType.STRING) @Column(name = "action_type", nullable = false, length = 64) private ProposalType actionType;
    @Enumerated(EnumType.STRING) @Column(name = "action_status", nullable = false, length = 64) private ProposalActionStatus actionStatus;
    @Column(name = "rule_code", length = 128) private String ruleCode;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "source_rule_version_id") private ReviewRuleVersionEntity sourceRuleVersion;
    @Column(name = "candidate_hash", length = 64) private String candidateHash;
    @Column(name = "before_rule_snapshot_json", columnDefinition = "text") private String beforeRuleSnapshotJson;
    @Column(name = "after_rule_snapshot_json", columnDefinition = "text") private String afterRuleSnapshotJson;
    @Column(name = "compare_result_json", columnDefinition = "text") private String compareResultJson;
    @Column(name = "validation_result_json", columnDefinition = "text") private String validationResultJson;
    @Column(name = "backtest_result_json", columnDefinition = "text") private String backtestResultJson;
    @Column(name = "affected_scope_json", columnDefinition = "text") private String affectedScopeJson;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "draft_rule_definition_id") private ReviewRuleDefinitionEntity draftRuleDefinition;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "draft_rule_version_id") private ReviewRuleVersionEntity draftRuleVersion;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private long version;

    public Long getId() { return id; }
    public RuleChangeProposalEntity getProposal() { return proposal; }
    public void setProposal(RuleChangeProposalEntity v) { proposal = v; }
    public int getSequenceNo() { return sequenceNo; }
    public void setSequenceNo(int v) { sequenceNo = v; }
    public ProposalType getActionType() { return actionType; }
    public void setActionType(ProposalType v) { actionType = v; }
    public ProposalActionStatus getActionStatus() { return actionStatus; }
    public void setActionStatus(ProposalActionStatus v) { actionStatus = v; }
    public String getRuleCode() { return ruleCode; }
    public void setRuleCode(String v) { ruleCode = v; }
    public ReviewRuleVersionEntity getSourceRuleVersion() { return sourceRuleVersion; }
    public void setSourceRuleVersion(ReviewRuleVersionEntity v) { sourceRuleVersion = v; }
    public String getCandidateHash() { return candidateHash; }
    public void setCandidateHash(String v) { candidateHash = v; }
    public String getBeforeRuleSnapshotJson() { return beforeRuleSnapshotJson; }
    public void setBeforeRuleSnapshotJson(String v) { beforeRuleSnapshotJson = v; }
    public String getAfterRuleSnapshotJson() { return afterRuleSnapshotJson; }
    public void setAfterRuleSnapshotJson(String v) { afterRuleSnapshotJson = v; }
    public String getCompareResultJson() { return compareResultJson; }
    public void setCompareResultJson(String v) { compareResultJson = v; }
    public String getValidationResultJson() { return validationResultJson; }
    public void setValidationResultJson(String v) { validationResultJson = v; }
    public String getBacktestResultJson() { return backtestResultJson; }
    public void setBacktestResultJson(String v) { backtestResultJson = v; }
    public String getAffectedScopeJson() { return affectedScopeJson; }
    public void setAffectedScopeJson(String v) { affectedScopeJson = v; }
    public ReviewRuleDefinitionEntity getDraftRuleDefinition() { return draftRuleDefinition; }
    public void setDraftRuleDefinition(ReviewRuleDefinitionEntity v) { draftRuleDefinition = v; }
    public ReviewRuleVersionEntity getDraftRuleVersion() { return draftRuleVersion; }
    public void setDraftRuleVersion(ReviewRuleVersionEntity v) { draftRuleVersion = v; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant v) { createdAt = v; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant v) { updatedAt = v; }
}
