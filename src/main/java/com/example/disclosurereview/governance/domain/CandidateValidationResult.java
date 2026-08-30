package com.example.disclosurereview.governance.domain;

import java.util.List;

public record CandidateValidationResult(
        boolean valid,
        String candidateHash,
        List<String> errors,
        List<String> warnings,
        List<String> conflicts
) {
}
