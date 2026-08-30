package com.example.disclosurereview.governance.service;

import com.example.disclosurereview.config.FeedbackGovernanceProperties;
import com.example.disclosurereview.governance.domain.GovernanceRunTriggerType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class FeedbackGovernanceScheduler {
    private final FeedbackGovernanceProperties properties;
    private final FeedbackGovernanceRunService runService;
    public FeedbackGovernanceScheduler(FeedbackGovernanceProperties properties, FeedbackGovernanceRunService runService) {
        this.properties = properties; this.runService = runService;
    }
    @Scheduled(cron = "${feedback-governance.cron:0 0 2 * * ?}")
    public void scan() {
        if (properties.isEnabled()) runService.start(GovernanceRunTriggerType.SCHEDULED, "SYSTEM");
    }
}
