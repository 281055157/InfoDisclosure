package com.example.disclosurereview.governance.agent;

/**
 * A completed Agent analysis that intentionally has no safe proposal. It is a
 * business outcome, not an infrastructure failure, so the MQ consumer must not
 * spend another full Agent budget retrying it.
 */
public class GovernanceNoProposalException extends RuntimeException {
    public GovernanceNoProposalException(String message) {
        super(message);
    }
}
