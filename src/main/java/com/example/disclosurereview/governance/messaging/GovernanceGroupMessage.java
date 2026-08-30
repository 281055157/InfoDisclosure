package com.example.disclosurereview.governance.messaging;

public record GovernanceGroupMessage(Long eventId, Long governanceRunId, Long groupId, int attempt) {}
