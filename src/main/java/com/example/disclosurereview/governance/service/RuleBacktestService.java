package com.example.disclosurereview.governance.service;

import com.example.disclosurereview.config.FeedbackGovernanceProperties;
import com.example.disclosurereview.governance.domain.BacktestExecutionStatus;
import com.example.disclosurereview.governance.domain.BacktestRiskLevel;
import com.example.disclosurereview.governance.domain.CandidateValidationResult;
import com.example.disclosurereview.governance.domain.RuleBacktestResult;
import com.example.disclosurereview.governance.domain.RuleBacktestSampleResult;
import com.example.disclosurereview.governance.domain.RuleCandidate;
import com.example.disclosurereview.governance.persistence.entity.RuleFeedbackGovernanceGroupEntity;
import com.example.disclosurereview.governance.persistence.repository.RuleFeedbackGovernanceGroupJpaRepository;
import com.example.disclosurereview.rule.domain.RuleExecutionStatus;
import com.example.disclosurereview.rule.domain.RuleExecutorType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RuleBacktestService {
    private final RuleFeedbackGovernanceGroupJpaRepository groupRepository;
    private final RuleBacktestSampleService sampleService;
    private final RuleExecutionSandbox sandbox;
    private final RuleCandidateValidationService validationService;
    private final FeedbackGovernanceProperties properties;
    private final GovernanceSemanticBacktestService semanticBacktestService;

    @Autowired
    public RuleBacktestService(RuleFeedbackGovernanceGroupJpaRepository groupRepository,
                               RuleBacktestSampleService sampleService,
                               RuleExecutionSandbox sandbox,
                               RuleCandidateValidationService validationService,
                               FeedbackGovernanceProperties properties,
                               GovernanceSemanticBacktestService semanticBacktestService) {
        this.groupRepository = groupRepository;
        this.sampleService = sampleService;
        this.sandbox = sandbox;
        this.validationService = validationService;
        this.properties = properties;
        this.semanticBacktestService = semanticBacktestService;
    }

    public RuleBacktestService(RuleFeedbackGovernanceGroupJpaRepository groupRepository,
                               RuleBacktestSampleService sampleService,
                               RuleExecutionSandbox sandbox,
                               RuleCandidateValidationService validationService,
                               FeedbackGovernanceProperties properties) {
        this(groupRepository, sampleService, sandbox, validationService, properties, null);
    }

    @Transactional
    public RuleBacktestResult run(Long groupId, RuleCandidate candidate, int maximumSamples) {
        return run(groupId, candidate, maximumSamples, null, null);
    }

    @Transactional
    public RuleBacktestResult run(Long groupId,
                                  RuleCandidate candidate,
                                  int maximumSamples,
                                  Long governanceRunId,
                                  Integer iterationNumber) {
        RuleFeedbackGovernanceGroupEntity group = groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("治理分组不存在: " + groupId));
        boolean creatingRule = candidate != null && candidate.ruleCode() != null
                && !candidate.ruleCode().equals(group.getRuleCode());
        CandidateValidationResult validation = validationService.validate(candidate, group.getRuleCode(), creatingRule);
        if (!validation.valid()) {
            throw new IllegalArgumentException("候选规则校验失败: " + String.join("; ", validation.errors()));
        }

        boolean semantic = isSemantic(candidate) && !Boolean.FALSE.equals(candidate.enabled());
        List<RuleBacktestSampleService.BacktestSample> samples = semantic
                ? sampleService.semanticSamples(group, Math.min(maximumSamples, properties.getBacktest().getMaximumLlmSamples()))
                : sampleService.samples(group, maximumSamples);
        List<RuleBacktestSampleResult> details;
        int llmCalls = 0;
        long inputTokens = 0, outputTokens = 0, cacheTokens = 0;
        List<String> warnings = coverageWarnings(group, samples);
        int uniqueDocumentCount = (int) samples.stream()
                .map(RuleBacktestSampleService.BacktestSample::taskId).distinct().count();
        if (uniqueDocumentCount < samples.size()) {
            warnings = append(warnings, "SAMPLES_SHARE_DOCUMENTS: samples=" + samples.size()
                    + ", uniqueDocuments=" + uniqueDocumentCount);
        }
        boolean semanticFailed = false;

        if (semantic && !properties.getBacktest().isLlmEnabled()) {
            details = samples.stream().map(sample -> sampleResult(sample,
                    sample.oldRuleMatched(), null, RuleExecutionStatus.INDETERMINATE.name(),
                    "LLM_RULE_BACKTEST_DISABLED", null, null, null, 0)).toList();
            warnings = append(warnings, "LLM_BACKTEST_DISABLED");
            semanticFailed = true;
        } else if (semantic && semanticBacktestService == null) {
            details = samples.stream().map(sample -> sampleResult(sample,
                    sample.oldRuleMatched(), null, RuleExecutionStatus.INDETERMINATE.name(),
                    "LLM_BACKTEST_SERVICE_UNAVAILABLE", null, null, null, 0)).toList();
            warnings = append(warnings, "LLM_BACKTEST_SERVICE_UNAVAILABLE");
            semanticFailed = true;
        } else if (semantic) {
            var outcome = semanticBacktestService.run(candidate, samples,
                    new GovernanceSemanticBacktestService.BacktestCallScope(governanceRunId, groupId, iterationNumber));
            llmCalls = outcome.llmCallCount();
            inputTokens = outcome.inputTokens();
            outputTokens = outcome.outputTokens();
            cacheTokens = outcome.cacheHitTokens();
            warnings = append(warnings, outcome.failures());
            semanticFailed = !outcome.failures().isEmpty();
            details = samples.stream().map(sample -> {
                var result = outcome.results().get(sample.sampleId());
                if (result == null) {
                    return sampleResult(sample, sample.oldRuleMatched(),
                            null, RuleExecutionStatus.INDETERMINATE.name(), "SEMANTIC_RESULT_MISSING",
                            null, null, null, 0);
                }
                return sampleResult(sample, sample.oldRuleMatched(),
                        result.matched(), result.status().name(), result.detail(), result.pageNumber(),
                        result.evidenceText(), result.explanation(), result.segmentCount());
            }).toList();
        } else {
            details = deterministic(candidate, samples);
        }

        Metrics metrics = metrics(samples, details);
        BacktestExecutionStatus executionStatus = executionStatus(semantic, llmCalls, metrics, semanticFailed);
        BacktestRiskLevel risk = risk(group.isRuleGap(), samples.size(), metrics.falsePositives(), metrics.confirmedPositives(),
                metrics.resolvedFalsePositives(), metrics.lostConfirmed(), metrics.unexpected(), metrics.unresolved());
        if (executionStatus != BacktestExecutionStatus.COMPLETED) risk = BacktestRiskLevel.HIGH;
        else if (semantic && !warnings.isEmpty() && risk == BacktestRiskLevel.LOW) risk = BacktestRiskLevel.MEDIUM;

        return new RuleBacktestResult(validation.candidateHash(), samples.size(), metrics.falsePositives(),
                metrics.confirmedPositives(), metrics.normal(), metrics.oldHits(), metrics.candidateHits(),
                metrics.resolvedFalsePositives(), metrics.remainingFalsePositives(), metrics.lostConfirmed(),
                metrics.unexpected(), metrics.unresolved(), risk, executionStatus,
                candidate.executorType() == null ? null : candidate.executorType().name(),
                metrics.determinate(), llmCalls, inputTokens, outputTokens, cacheTokens,
                uniqueDocumentCount, warnings, details);
    }

    public String cacheFingerprint() {
        var backtest = properties.getBacktest();
        return String.join("|",
                nullToEmpty(backtest.getExecutionVersion()),
                "llm=" + backtest.isLlmEnabled(),
                "samples=" + backtest.getMaximumLlmSamples(),
                "requestChars=" + backtest.getMaximumRequestChars(),
                "windowChars=" + backtest.getSampleWindowChars(),
                "overlap=" + backtest.getWindowOverlapChars(),
                "prompt=" + nullToEmpty(backtest.getPromptVersion()));
    }

    public void requireUsableForProposal(RuleBacktestResult result, RuleCandidate candidate) {
        if (result == null || result.executionStatus() == null
                || result.executionStatus() == BacktestExecutionStatus.UNAVAILABLE) {
            throw new IllegalArgumentException("回测不可用，不能满足规则变更提案前置条件");
        }
        if (result.determinateSampleCount() < properties.getMinimumFeedbackCount()) {
            throw new IllegalArgumentException("可判定回测样本不足 " + properties.getMinimumFeedbackCount() + " 个");
        }
        if (isSemantic(candidate) && !Boolean.FALSE.equals(candidate.enabled()) && result.llmCallCount() < 1) {
            throw new IllegalArgumentException("语义候选规则未执行有效 LLM 回测");
        }
    }

    private List<RuleBacktestSampleResult> deterministic(RuleCandidate candidate,
                                                         List<RuleBacktestSampleService.BacktestSample> samples) {
        List<RuleBacktestSampleResult> result = new ArrayList<>();
        Map<Long, RuleExecutionSandbox.SandboxResult> taskResults = new LinkedHashMap<>();
        for (RuleBacktestSampleService.BacktestSample sample : samples) {
            RuleExecutionSandbox.SandboxResult sandboxResult = taskResults.computeIfAbsent(
                    sample.taskId(), taskId -> sandbox.executeCandidate(candidate, taskId));
            result.add(sampleResult(sample, sample.oldRuleMatched(), sandboxResult.matched(),
                    sandboxResult.status().name(), sandboxResult.detail(), null, null, null, 0));
        }
        return List.copyOf(result);
    }

    private RuleBacktestSampleResult sampleResult(RuleBacktestSampleService.BacktestSample sample,
                                                  Boolean oldRuleMatched,
                                                  Boolean candidateRuleMatched,
                                                  String status,
                                                  String detail,
                                                  Integer pageNumber,
                                                  String evidenceText,
                                                  String explanation,
                                                  int segmentCount) {
        return new RuleBacktestSampleResult(sample.taskId(), sample.sampleType(), oldRuleMatched,
                candidateRuleMatched, status, detail, pageNumber, evidenceText, explanation, segmentCount,
                sample.sampleId(), sample.feedbackId(), sample.issueId());
    }

    private Metrics metrics(List<RuleBacktestSampleService.BacktestSample> samples,
                            List<RuleBacktestSampleResult> details) {
        int fp = 0, confirmed = 0, normal = 0, oldHits = 0, candidateHits = 0;
        int resolvedFp = 0, remainingFp = 0, lostConfirmed = 0, unexpected = 0, unresolved = 0;
        for (int i = 0; i < samples.size(); i++) {
            var sample = samples.get(i);
            var detail = details.get(i);
            if ("FALSE_POSITIVE".equals(sample.sampleType())) fp++;
            else if ("CONFIRMED_POSITIVE".equals(sample.sampleType()) || "FALSE_NEGATIVE".equals(sample.sampleType())) confirmed++;
            else if ("NORMAL".equals(sample.sampleType())) normal++;
            if (sample.oldRuleMatched()) oldHits++;
            if (Boolean.TRUE.equals(detail.candidateRuleMatched())) candidateHits++;
            if (detail.candidateRuleMatched() == null) unresolved++;
            else if ("FALSE_POSITIVE".equals(sample.sampleType())) {
                if (detail.candidateRuleMatched()) remainingFp++; else resolvedFp++;
            } else if (("CONFIRMED_POSITIVE".equals(sample.sampleType()) || "FALSE_NEGATIVE".equals(sample.sampleType()))
                    && !detail.candidateRuleMatched()) lostConfirmed++;
            else if ("NORMAL".equals(sample.sampleType()) && detail.candidateRuleMatched()) unexpected++;
        }
        return new Metrics(fp, confirmed, normal, oldHits, candidateHits, resolvedFp, remainingFp,
                lostConfirmed, unexpected, unresolved, Math.max(0, samples.size() - unresolved));
    }

    private BacktestExecutionStatus executionStatus(boolean semantic,
                                                     int llmCalls,
                                                     Metrics metrics,
                                                     boolean semanticFailed) {
        if (!semantic) return metrics.unresolved() == 0
                ? BacktestExecutionStatus.COMPLETED : BacktestExecutionStatus.PARTIAL;
        if (!properties.getBacktest().isLlmEnabled() || llmCalls < 1
                || metrics.determinate() < properties.getMinimumFeedbackCount()) {
            return BacktestExecutionStatus.UNAVAILABLE;
        }
        return semanticFailed || metrics.unresolved() > 0
                ? BacktestExecutionStatus.PARTIAL : BacktestExecutionStatus.COMPLETED;
    }

    private List<String> coverageWarnings(RuleFeedbackGovernanceGroupEntity group,
                                          List<RuleBacktestSampleService.BacktestSample> samples) {
        List<String> warnings = new ArrayList<>();
        String requiredFeedbackType = group.isRuleGap() ? "FALSE_NEGATIVE" : "FALSE_POSITIVE";
        if (samples.stream().noneMatch(sample -> requiredFeedbackType.equals(sample.sampleType())))
            warnings.add(requiredFeedbackType + "_SAMPLE_MISSING");
        if (samples.stream().noneMatch(sample -> "CONFIRMED_POSITIVE".equals(sample.sampleType())))
            warnings.add("CONFIRMED_POSITIVE_SAMPLE_MISSING");
        if (samples.stream().noneMatch(sample -> "NORMAL".equals(sample.sampleType())))
            warnings.add("NORMAL_SAMPLE_MISSING");
        return List.copyOf(warnings);
    }

    private List<String> append(List<String> original, String value) {
        List<String> result = new ArrayList<>(original);
        result.add(value);
        return List.copyOf(result);
    }

    private List<String> append(List<String> original, List<String> values) {
        if (values == null || values.isEmpty()) return original;
        List<String> result = new ArrayList<>(original);
        result.addAll(values);
        return List.copyOf(result);
    }

    private boolean isSemantic(RuleCandidate candidate) {
        return candidate != null && (candidate.executorType() == RuleExecutorType.LLM_POLICY
                || candidate.executorType() == RuleExecutorType.HYBRID);
    }

    private BacktestRiskLevel risk(boolean ruleGap,
                                   int samples,
                                   int falsePositives,
                                   int confirmedPositives,
                                   int resolvedFalsePositives,
                                   int lostConfirmed,
                                   int unexpected,
                                   int unresolved) {
        if (samples < properties.getMinimumFeedbackCount()
                || (!ruleGap && falsePositives == 0)
                || (ruleGap && confirmedPositives == 0)) return BacktestRiskLevel.HIGH;
        if (lostConfirmed > Math.max(1, confirmedPositives / 5) || unexpected > 0 || unresolved > samples / 3) {
            return BacktestRiskLevel.HIGH;
        }
        boolean resolvesMost = resolvedFalsePositives * 2 >= falsePositives;
        if (resolvesMost && lostConfirmed == 0 && unresolved == 0) return BacktestRiskLevel.LOW;
        return BacktestRiskLevel.MEDIUM;
    }

    private String nullToEmpty(String value) { return value == null ? "" : value; }

    private record Metrics(int falsePositives,
                           int confirmedPositives,
                           int normal,
                           int oldHits,
                           int candidateHits,
                           int resolvedFalsePositives,
                           int remainingFalsePositives,
                           int lostConfirmed,
                           int unexpected,
                           int unresolved,
                           int determinate) {}
}
