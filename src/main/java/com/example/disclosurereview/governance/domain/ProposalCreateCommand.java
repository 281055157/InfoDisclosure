package com.example.disclosurereview.governance.domain;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public record ProposalCreateCommand(
        ProposalType proposalType,
        RootCauseType rootCauseType,
        String problemSummary,
        String rootCauseAnalysis,
        String changeReason,
        String expectedEffect,
        String riskDescription,
        Double agentConfidence,
        JsonNode candidateRule,
        String optimizationCategory,
        String optimizationAdvice,
        String responsibleModule,
        String priority,
        Boolean humanFollowUpRequired,
        List<ProposalActionCommand> actions
) {
    public ProposalCreateCommand(
            ProposalType proposalType,
            RootCauseType rootCauseType,
            String problemSummary,
            String rootCauseAnalysis,
            String changeReason,
            String expectedEffect,
            String riskDescription,
            Double agentConfidence,
            JsonNode candidateRule,
            String optimizationCategory,
            String optimizationAdvice,
            String responsibleModule,
            String priority,
            Boolean humanFollowUpRequired
    ) {
        this(proposalType, rootCauseType, problemSummary, rootCauseAnalysis, changeReason, expectedEffect,
                riskDescription, agentConfidence, candidateRule, optimizationCategory, optimizationAdvice,
                responsibleModule, priority, humanFollowUpRequired, List.of());
    }
}
