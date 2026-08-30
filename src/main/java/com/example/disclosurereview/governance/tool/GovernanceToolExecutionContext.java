package com.example.disclosurereview.governance.tool;

public record GovernanceToolExecutionContext(
        Long governanceRunId,
        Long governanceGroupId,
        int iterationNumber,
        String operator,
        int toolIndex,
        String executionMode,
        String parallelGroup,
        String traceParentSpanId
) {
    public GovernanceToolExecutionContext(Long governanceRunId, Long governanceGroupId,
                                          int iterationNumber, String operator) {
        this(governanceRunId, governanceGroupId, iterationNumber, operator,
                1, "SERIAL", null, null);
    }
}
