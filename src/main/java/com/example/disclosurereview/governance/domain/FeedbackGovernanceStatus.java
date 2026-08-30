package com.example.disclosurereview.governance.domain;

import java.util.Set;

public enum FeedbackGovernanceStatus {
    PENDING, GROUPED, ANALYZING, PROPOSAL_CREATED, DEFERRED, RESOLVED, FAILED;

    public static final Set<String> PENDING_DATABASE_VALUES = Set.of("NEW", PENDING.name());
}
