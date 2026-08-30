package com.example.disclosurereview.governance.domain;

public enum ProposalStatus {
    DRAFT, PENDING_REVIEW, APPROVED, APPROVED_WITH_MODIFICATION, REJECTED, APPLIED, DEFERRED, CLOSED, FAILED;

    public boolean active() {
        return this == DRAFT || this == PENDING_REVIEW || this == APPROVED || this == APPROVED_WITH_MODIFICATION;
    }
}
