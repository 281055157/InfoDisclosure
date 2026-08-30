package com.example.disclosurereview.rule.domain;

import java.util.ArrayList;
import java.util.List;

public record RuleValidationResult(
        boolean valid,
        List<String> errors
) {
    public RuleValidationResult {
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public static RuleValidationResult ok() {
        return new RuleValidationResult(true, List.of());
    }

    public static RuleValidationResult invalid(String error) {
        return new RuleValidationResult(false, List.of(error));
    }

    public static RuleValidationResult invalid(List<String> errors) {
        return new RuleValidationResult(false, errors);
    }

    public RuleValidationResult merge(RuleValidationResult other) {
        if (other == null || other.valid()) {
            return this;
        }
        List<String> merged = new ArrayList<>(errors);
        merged.addAll(other.errors());
        return new RuleValidationResult(false, merged);
    }
}
