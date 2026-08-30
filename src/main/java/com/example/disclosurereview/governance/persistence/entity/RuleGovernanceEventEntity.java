package com.example.disclosurereview.governance.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "rule_governance_event")
public class RuleGovernanceEventEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "governance_run_id", nullable = false) private RuleGovernanceRunEntity governanceRun;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "governance_group_id", nullable = false) private RuleFeedbackGovernanceGroupEntity governanceGroup;
    @Column(name = "event_type", nullable = false, length = 64) private String eventType;
    @Column(name = "event_status", nullable = false, length = 32) private String eventStatus;
    @Column(name = "payload_json", columnDefinition = "text") private String payloadJson;
    @Column(name = "retry_count", nullable = false) private int retryCount;
    @Column(name = "error_message", columnDefinition = "text") private String errorMessage;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "published_at") private Instant publishedAt;
    @Column(name = "completed_at") private Instant completedAt;
    @Version private long version;
    public Long getId() { return id; }
    public RuleGovernanceRunEntity getGovernanceRun() { return governanceRun; }
    public void setGovernanceRun(RuleGovernanceRunEntity v) { governanceRun = v; }
    public RuleFeedbackGovernanceGroupEntity getGovernanceGroup() { return governanceGroup; }
    public void setGovernanceGroup(RuleFeedbackGovernanceGroupEntity v) { governanceGroup = v; }
    public String getEventType() { return eventType; }
    public void setEventType(String v) { eventType = v; }
    public String getEventStatus() { return eventStatus; }
    public void setEventStatus(String v) { eventStatus = v; }
    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String v) { payloadJson = v; }
    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int v) { retryCount = v; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String v) { errorMessage = v; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant v) { createdAt = v; }
    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant v) { publishedAt = v; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant v) { completedAt = v; }
    public long getVersion() { return version; }
}
