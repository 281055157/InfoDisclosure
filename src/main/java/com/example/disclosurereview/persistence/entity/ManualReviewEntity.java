package com.example.disclosurereview.persistence.entity;

import com.example.disclosurereview.model.ManualReviewDecision;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "manual_review")
public class ManualReviewEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private ReviewTaskEntity task;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_decision", nullable = false, length = 64)
    private ManualReviewDecision reviewDecision;

    @Column(name = "review_comment", columnDefinition = "text")
    private String reviewComment;

    @Column(name = "ai_result_correct")
    private Boolean aiResultCorrect;

    @Column(name = "contains_false_positive")
    private Boolean containsFalsePositive;

    @Column(name = "contains_false_negative")
    private Boolean containsFalseNegative;

    @Column(name = "actual_issue_types", columnDefinition = "text")
    private String actualIssueTypes;

    @Column(name = "reviewer", length = 128)
    private String reviewer;

    @Column(name = "reviewed_at", nullable = false)
    private Instant reviewedAt;

    public Long getId() {
        return id;
    }

    public ReviewTaskEntity getTask() {
        return task;
    }

    public void setTask(ReviewTaskEntity task) {
        this.task = task;
    }

    public ManualReviewDecision getReviewDecision() {
        return reviewDecision;
    }

    public void setReviewDecision(ManualReviewDecision reviewDecision) {
        this.reviewDecision = reviewDecision;
    }

    public String getReviewComment() {
        return reviewComment;
    }

    public void setReviewComment(String reviewComment) {
        this.reviewComment = reviewComment;
    }

    public Boolean getAiResultCorrect() {
        return aiResultCorrect;
    }

    public void setAiResultCorrect(Boolean aiResultCorrect) {
        this.aiResultCorrect = aiResultCorrect;
    }

    public Boolean getContainsFalsePositive() {
        return containsFalsePositive;
    }

    public void setContainsFalsePositive(Boolean containsFalsePositive) {
        this.containsFalsePositive = containsFalsePositive;
    }

    public Boolean getContainsFalseNegative() {
        return containsFalseNegative;
    }

    public void setContainsFalseNegative(Boolean containsFalseNegative) {
        this.containsFalseNegative = containsFalseNegative;
    }

    public String getActualIssueTypes() {
        return actualIssueTypes;
    }

    public void setActualIssueTypes(String actualIssueTypes) {
        this.actualIssueTypes = actualIssueTypes;
    }

    public String getReviewer() {
        return reviewer;
    }

    public void setReviewer(String reviewer) {
        this.reviewer = reviewer;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(Instant reviewedAt) {
        this.reviewedAt = reviewedAt;
    }
}
