package com.example.disclosurereview.governance.domain;

import org.springframework.stereotype.Component;

import java.util.EnumSet;

@Component
public class GovernanceActionPolicy {
    public boolean allowed(RootCauseType rootCause, ProposalType proposalType) {
        if (rootCause == null || proposalType == null) return false;
        return switch (rootCause) {
            case RULE_SCOPE -> EnumSet.of(ProposalType.UPDATE_RULE, ProposalType.CREATE_EXCEPTION).contains(proposalType);
            case RULE_CONFIG -> EnumSet.of(ProposalType.UPDATE_RULE, ProposalType.DISABLE_RULE,
                    ProposalType.CREATE_RULE, ProposalType.COMPOSITE_RULE_CHANGE).contains(proposalType);
            case RULE_EXECUTOR -> EnumSet.of(ProposalType.UPDATE_RULE, ProposalType.CREATE_RULE,
                    ProposalType.COMPOSITE_RULE_CHANGE).contains(proposalType);
            case RULE_EXCEPTION -> proposalType == ProposalType.CREATE_EXCEPTION;
            case PRODUCT_DATA, DOCUMENT_PARSING, HUMAN_INCONSISTENCY -> proposalType == ProposalType.OPTIMIZATION_ADVICE;
            case LLM_POLICY -> EnumSet.of(ProposalType.UPDATE_RULE, ProposalType.CREATE_RULE, ProposalType.COMPOSITE_RULE_CHANGE,
                    ProposalType.OPTIMIZATION_ADVICE).contains(proposalType);
            case INSUFFICIENT_EVIDENCE -> EnumSet.of(ProposalType.NO_ACTION, ProposalType.OPTIMIZATION_ADVICE).contains(proposalType);
            case NO_ACTION -> proposalType == ProposalType.NO_ACTION;
        };
    }

    public void requireAllowed(RootCauseType rootCause, ProposalType proposalType) {
        if (!allowed(rootCause, proposalType)) {
            throw new IllegalArgumentException("不允许的根因与治理动作组合: " + rootCause + " -> " + proposalType);
        }
    }
}
