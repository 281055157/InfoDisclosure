package com.example.disclosurereview.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "feedback-governance")
public class FeedbackGovernanceProperties {

    private boolean enabled = false;
    private String cron = "0 0 2 * * ?";
    private int minimumFeedbackCount = 1;
    private int lookbackDays = 90;
    private int maximumGroupsPerRun = 20;
    private int maximumSamplesPerGroup = 10;
    private final Agent agent = new Agent();
    private final Backtest backtest = new Backtest();
    private final Rabbitmq rabbitmq = new Rabbitmq();
    private final EffectEvaluation effectEvaluation = new EffectEvaluation();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getCron() { return cron; }
    public void setCron(String cron) { this.cron = cron; }
    public int getMinimumFeedbackCount() { return minimumFeedbackCount; }
    public void setMinimumFeedbackCount(int value) { this.minimumFeedbackCount = Math.max(1, value); }
    public int getLookbackDays() { return lookbackDays; }
    public void setLookbackDays(int value) { this.lookbackDays = Math.max(1, value); }
    public int getMaximumGroupsPerRun() { return maximumGroupsPerRun; }
    public void setMaximumGroupsPerRun(int value) { this.maximumGroupsPerRun = Math.max(1, value); }
    public int getMaximumSamplesPerGroup() { return maximumSamplesPerGroup; }
    public void setMaximumSamplesPerGroup(int value) { this.maximumSamplesPerGroup = Math.max(1, value); }
    public Agent getAgent() { return agent; }
    public Backtest getBacktest() { return backtest; }
    public Rabbitmq getRabbitmq() { return rabbitmq; }
    public EffectEvaluation getEffectEvaluation() { return effectEvaluation; }

    public static class Agent {
        // Model conversation rounds and tool-call volume are intentionally bounded independently.
        private int maxModelIterations = 16;
        private int maxToolsPerRound = 6;
        private int maxTotalToolCalls = 32;
        private int parallelToolThreads = 4;
        private int timeoutSeconds = 300;
        private double minimumConfidenceForRuleChange = 0.70;
        private String toolCallingMode = "STRUCTURED";
        private String promptVersion = "governance-v1";
        private int maximumDocumentContextChars = 12000;

        public int getMaxModelIterations() { return maxModelIterations; }
        public void setMaxModelIterations(int value) { this.maxModelIterations = Math.max(1, value); }
        /** Backward-compatible property alias; it now limits model rounds rather than individual tools. */
        public int getMaxToolIterations() { return maxModelIterations; }
        public void setMaxToolIterations(int value) { this.maxModelIterations = Math.max(1, value); }
        public int getMaxToolsPerRound() { return maxToolsPerRound; }
        public void setMaxToolsPerRound(int value) { this.maxToolsPerRound = Math.max(1, value); }
        public int getMaxTotalToolCalls() { return maxTotalToolCalls; }
        public void setMaxTotalToolCalls(int value) { this.maxTotalToolCalls = Math.max(1, value); }
        public int getParallelToolThreads() { return parallelToolThreads; }
        public void setParallelToolThreads(int value) { this.parallelToolThreads = Math.max(1, value); }
        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int value) { this.timeoutSeconds = Math.max(1, value); }
        public Duration timeout() { return Duration.ofSeconds(timeoutSeconds); }
        public double getMinimumConfidenceForRuleChange() { return minimumConfidenceForRuleChange; }
        public void setMinimumConfidenceForRuleChange(double value) { this.minimumConfidenceForRuleChange = value; }
        public String getToolCallingMode() { return toolCallingMode; }
        public void setToolCallingMode(String value) { this.toolCallingMode = value; }
        public String getPromptVersion() { return promptVersion; }
        public void setPromptVersion(String value) { this.promptVersion = value; }
        public int getMaximumDocumentContextChars() { return maximumDocumentContextChars; }
        public void setMaximumDocumentContextChars(int value) { this.maximumDocumentContextChars = Math.max(1000, value); }
    }

    public static class Backtest {
        private int maximumSamples = 100;
        private int maximumLlmSamples = 5;
        private boolean includeConfirmedPositiveSamples = true;
        private boolean includeNormalSamples = true;
        private boolean llmEnabled = true;
        private int maximumRequestChars = 30_000;
        private int sampleWindowChars = 6_000;
        private int windowOverlapChars = 300;
        private String executionVersion = "semantic-backtest-v3";
        private String promptVersion = "governance-backtest-v3";

        public int getMaximumSamples() { return maximumSamples; }
        public void setMaximumSamples(int value) { this.maximumSamples = Math.max(1, value); }
        public int getMaximumLlmSamples() { return maximumLlmSamples; }
        public void setMaximumLlmSamples(int value) { this.maximumLlmSamples = Math.max(0, value); }
        public boolean isIncludeConfirmedPositiveSamples() { return includeConfirmedPositiveSamples; }
        public void setIncludeConfirmedPositiveSamples(boolean value) { this.includeConfirmedPositiveSamples = value; }
        public boolean isIncludeNormalSamples() { return includeNormalSamples; }
        public void setIncludeNormalSamples(boolean value) { this.includeNormalSamples = value; }
        public boolean isLlmEnabled() { return llmEnabled; }
        public void setLlmEnabled(boolean value) { this.llmEnabled = value; }
        public int getMaximumRequestChars() { return maximumRequestChars; }
        public void setMaximumRequestChars(int value) { this.maximumRequestChars = Math.max(1_000, value); }
        public int getSampleWindowChars() { return sampleWindowChars; }
        public void setSampleWindowChars(int value) { this.sampleWindowChars = Math.max(500, value); }
        public int getWindowOverlapChars() { return windowOverlapChars; }
        public void setWindowOverlapChars(int value) { this.windowOverlapChars = Math.max(0, value); }
        public String getExecutionVersion() { return executionVersion; }
        public void setExecutionVersion(String value) { this.executionVersion = value; }
        public String getPromptVersion() { return promptVersion; }
        public void setPromptVersion(String value) { this.promptVersion = value; }
    }

    public static class Rabbitmq {
        private String exchange = "feedback.governance.exchange";
        private String queue = "feedback.governance.group.analyze";
        private String routingKey = "feedback.governance.group.analyze";
        private String deadLetterExchange = "feedback.governance.dlx";
        private String deadLetterQueue = "feedback.governance.group.analyze.dlq";
        private boolean pendingPublishEnabled = true;
        private long pendingPublishDelayMs = 10000;
        private int maximumAttempts = 3;

        public String getExchange() { return exchange; }
        public void setExchange(String value) { this.exchange = value; }
        public String getQueue() { return queue; }
        public void setQueue(String value) { this.queue = value; }
        public String getRoutingKey() { return routingKey; }
        public void setRoutingKey(String value) { this.routingKey = value; }
        public String getDeadLetterExchange() { return deadLetterExchange; }
        public void setDeadLetterExchange(String value) { this.deadLetterExchange = value; }
        public String getDeadLetterQueue() { return deadLetterQueue; }
        public void setDeadLetterQueue(String value) { this.deadLetterQueue = value; }
        public boolean isPendingPublishEnabled() { return pendingPublishEnabled; }
        public void setPendingPublishEnabled(boolean value) { this.pendingPublishEnabled = value; }
        public long getPendingPublishDelayMs() { return pendingPublishDelayMs; }
        public void setPendingPublishDelayMs(long value) { this.pendingPublishDelayMs = Math.max(1000, value); }
        public int getMaximumAttempts() { return maximumAttempts; }
        public void setMaximumAttempts(int value) { this.maximumAttempts = Math.max(1, value); }
    }

    public static class EffectEvaluation {
        private boolean enabled = false;
        private int minimumExecutionCount = 20;
        private int evaluationDays = 30;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean value) { this.enabled = value; }
        public int getMinimumExecutionCount() { return minimumExecutionCount; }
        public void setMinimumExecutionCount(int value) { this.minimumExecutionCount = Math.max(1, value); }
        public int getEvaluationDays() { return evaluationDays; }
        public void setEvaluationDays(int value) { this.evaluationDays = Math.max(1, value); }
    }
}
