package com.example.disclosurereview.governance.persistence.entity;

import com.example.disclosurereview.persistence.entity.ReviewRuleFeedbackEntity;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "rule_feedback_governance_group_item")
public class RuleFeedbackGovernanceGroupItemEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "group_id", nullable = false) private RuleFeedbackGovernanceGroupEntity group;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "feedback_id", nullable = false) private ReviewRuleFeedbackEntity feedback;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    public Long getId() { return id; }
    public RuleFeedbackGovernanceGroupEntity getGroup() { return group; }
    public void setGroup(RuleFeedbackGovernanceGroupEntity v) { group = v; }
    public ReviewRuleFeedbackEntity getFeedback() { return feedback; }
    public void setFeedback(ReviewRuleFeedbackEntity v) { feedback = v; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant v) { createdAt = v; }
}
