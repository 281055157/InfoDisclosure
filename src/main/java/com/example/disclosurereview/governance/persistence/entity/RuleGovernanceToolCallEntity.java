package com.example.disclosurereview.governance.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "rule_governance_tool_call")
public class RuleGovernanceToolCallEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "governance_run_id", nullable = false) private RuleGovernanceRunEntity governanceRun;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "governance_group_id", nullable = false) private RuleFeedbackGovernanceGroupEntity governanceGroup;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "proposal_id") private RuleChangeProposalEntity proposal;
    @Column(name = "iteration_number", nullable = false) private int iterationNumber;
    @Column(name = "tool_index", nullable = false) private int toolIndex = 1;
    @Column(name = "execution_mode", nullable = false, length = 16) private String executionMode = "SERIAL";
    @Column(name = "parallel_group", length = 96) private String parallelGroup;
    @Column(name = "tool_name", nullable = false, length = 128) private String toolName;
    @Column(name = "argument_hash", nullable = false, length = 64) private String argumentHash;
    @Column(name = "candidate_hash", length = 64) private String candidateHash;
    @Column(name = "input_json", columnDefinition = "text") private String inputJson;
    @Column(name = "output_json", columnDefinition = "text") private String outputJson;
    @Column(name = "call_status", nullable = false, length = 32) private String callStatus;
    @Column(name = "error_message", columnDefinition = "text") private String errorMessage;
    @Column(name = "duration_ms") private Long durationMs;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    public Long getId() { return id; }
    public RuleGovernanceRunEntity getGovernanceRun() { return governanceRun; }
    public void setGovernanceRun(RuleGovernanceRunEntity v) { governanceRun = v; }
    public RuleFeedbackGovernanceGroupEntity getGovernanceGroup() { return governanceGroup; }
    public void setGovernanceGroup(RuleFeedbackGovernanceGroupEntity v) { governanceGroup = v; }
    public RuleChangeProposalEntity getProposal() { return proposal; }
    public void setProposal(RuleChangeProposalEntity v) { proposal = v; }
    public int getIterationNumber() { return iterationNumber; }
    public void setIterationNumber(int v) { iterationNumber = v; }
    public int getToolIndex() { return toolIndex; }
    public void setToolIndex(int v) { toolIndex = v; }
    public String getExecutionMode() { return executionMode; }
    public void setExecutionMode(String v) { executionMode = v; }
    public String getParallelGroup() { return parallelGroup; }
    public void setParallelGroup(String v) { parallelGroup = v; }
    public String getToolName() { return toolName; }
    public void setToolName(String v) { toolName = v; }
    public String getArgumentHash() { return argumentHash; }
    public void setArgumentHash(String v) { argumentHash = v; }
    public String getCandidateHash() { return candidateHash; }
    public void setCandidateHash(String v) { candidateHash = v; }
    public String getInputJson() { return inputJson; }
    public void setInputJson(String v) { inputJson = v; }
    public String getOutputJson() { return outputJson; }
    public void setOutputJson(String v) { outputJson = v; }
    public String getCallStatus() { return callStatus; }
    public void setCallStatus(String v) { callStatus = v; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String v) { errorMessage = v; }
    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long v) { durationMs = v; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant v) { createdAt = v; }
}
