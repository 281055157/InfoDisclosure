package com.example.disclosurereview.rule.domain;

public enum RuleExecutorType {
    REGEX,
    REQUIRED,
    ENUM_MAPPING,
    NUMERIC_RANGE,
    LLM_POLICY,
    HYBRID,
    JAVA_PLUGIN
}
