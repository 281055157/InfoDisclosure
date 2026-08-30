package com.example.disclosurereview.governance.persistence.entity;

import com.example.disclosurereview.persistence.entity.ReviewRuleFeedbackEntity;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "rule_change_proposal_feedback")
public class RuleChangeProposalFeedbackEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "proposal_id", nullable = false) private RuleChangeProposalEntity proposal;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "feedback_id", nullable = false) private ReviewRuleFeedbackEntity feedback;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    public Long getId() { return id; }
    public RuleChangeProposalEntity getProposal() { return proposal; }
    public void setProposal(RuleChangeProposalEntity v) { proposal = v; }
    public ReviewRuleFeedbackEntity getFeedback() { return feedback; }
    public void setFeedback(ReviewRuleFeedbackEntity v) { feedback = v; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant v) { createdAt = v; }
}
