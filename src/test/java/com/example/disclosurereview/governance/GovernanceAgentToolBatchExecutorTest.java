package com.example.disclosurereview.governance;

import com.example.disclosurereview.governance.agent.GovernanceAgentToolBatchExecutor;
import com.example.disclosurereview.governance.tool.GovernanceAgentToolRegistry;
import com.example.disclosurereview.governance.tool.ToolExecutionResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GovernanceAgentToolBatchExecutorTest {
    @Test
    void parallelSafeToolsActuallyOverlapAndShareParallelGroup() throws Exception {
        GovernanceAgentToolRegistry registry = mock(GovernanceAgentToolRegistry.class);
        when(registry.parallelSafe(anyString())).thenReturn(true);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximumActive = new AtomicInteger();
        when(registry.execute(anyString(), any(), any())).thenAnswer(invocation -> {
            int now = active.incrementAndGet();
            maximumActive.accumulateAndGet(now, Math::max);
            Thread.sleep(120);
            active.decrementAndGet();
            return ToolExecutionResult.read(new ObjectMapper().createObjectNode().put("ok", true));
        });
        var executor = Executors.newFixedThreadPool(2);
        try {
            var batch = new GovernanceAgentToolBatchExecutor(registry, executor).execute(1L, 2L, 1, List.of(
                    new GovernanceAgentToolBatchExecutor.ToolRequest("a", "getFeedbackSamples", new ObjectMapper().createObjectNode(), 1),
                    new GovernanceAgentToolBatchExecutor.ToolRequest("b", "getHistoricalGovernanceDecisions", new ObjectMapper().createObjectNode(), 2)));

            assertThat(batch.executionMode()).isEqualTo("PARALLEL");
            assertThat(batch.outcomes()).hasSize(2).allMatch(GovernanceAgentToolBatchExecutor.ToolOutcome::success);
            assertThat(maximumActive.get()).isEqualTo(2);
            assertThat(batch.outcomes()).extracting(GovernanceAgentToolBatchExecutor.ToolOutcome::parallelGroup)
                    .doesNotContainNull().allMatch(batch.outcomes().get(0).parallelGroup()::equals);
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(2, TimeUnit.SECONDS);
        }
    }

    @Test
    void proposalToolForcesSerialExecution() {
        GovernanceAgentToolRegistry registry = mock(GovernanceAgentToolRegistry.class);
        when(registry.parallelSafe("getFeedbackSamples")).thenReturn(true);
        when(registry.parallelSafe("proposeNoAction")).thenReturn(false);
        when(registry.execute(eq("getFeedbackSamples"), any(), any()))
                .thenReturn(ToolExecutionResult.read(new ObjectMapper().createObjectNode()));
        when(registry.execute(eq("proposeNoAction"), any(), any()))
                .thenReturn(ToolExecutionResult.proposal(new ObjectMapper().createObjectNode(), 9L));

        var batch = new GovernanceAgentToolBatchExecutor(registry, Runnable::run).execute(1L, 2L, 2, List.of(
                new GovernanceAgentToolBatchExecutor.ToolRequest("a", "getFeedbackSamples", new ObjectMapper().createObjectNode(), 1),
                new GovernanceAgentToolBatchExecutor.ToolRequest("p", "proposeNoAction", new ObjectMapper().createObjectNode(), 2)));

        assertThat(batch.executionMode()).isEqualTo("SERIAL");
        assertThat(batch.proposalId()).isEqualTo(9L);
        assertThat(batch.outcomes()).extracting(GovernanceAgentToolBatchExecutor.ToolOutcome::executionMode)
                .containsOnly("SERIAL");
    }
}
