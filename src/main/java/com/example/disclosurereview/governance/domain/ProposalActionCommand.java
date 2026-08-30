package com.example.disclosurereview.governance.domain;

import com.fasterxml.jackson.databind.JsonNode;

public record ProposalActionCommand(
        ProposalType actionType,
        String ruleCode,
        Long sourceRuleVersionId,
        JsonNode candidateRule
) {
}
