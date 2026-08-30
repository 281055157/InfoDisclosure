package com.example.disclosurereview.governance.service;

import com.example.disclosurereview.config.FeedbackGovernanceProperties;
import com.example.disclosurereview.governance.persistence.entity.RuleFeedbackGovernanceGroupEntity;
import com.example.disclosurereview.model.ReviewIssueStatus;
import com.example.disclosurereview.persistence.entity.ReviewIssueEntity;
import com.example.disclosurereview.persistence.entity.ReviewRuleExecutionEntity;
import com.example.disclosurereview.persistence.repository.ReviewIssueJpaRepository;
import com.example.disclosurereview.persistence.repository.ReviewRuleExecutionJpaRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class RuleBacktestSampleService {
    private final FeedbackGovernanceGroupService groupService;
    private final ReviewIssueJpaRepository issueRepository;
    private final ReviewRuleExecutionJpaRepository executionRepository;
    private final FeedbackGovernanceProperties properties;

    public RuleBacktestSampleService(FeedbackGovernanceGroupService groupService,
                                     ReviewIssueJpaRepository issueRepository,
                                     ReviewRuleExecutionJpaRepository executionRepository,
                                     FeedbackGovernanceProperties properties) {
        this.groupService = groupService;
        this.issueRepository = issueRepository;
        this.executionRepository = executionRepository;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public List<BacktestSample> samples(RuleFeedbackGovernanceGroupEntity group, int requestedMaximum) {
        int maximum = Math.max(1, Math.min(requestedMaximum, properties.getBacktest().getMaximumSamples()));
        Map<String, BacktestSample> samples = new LinkedHashMap<>();
        groupService.feedbacks(group.getId()).forEach(feedback -> {
            BacktestSample sample = feedbackSample(feedback);
            samples.putIfAbsent(sample.sampleId(), sample);
        });

        int querySize = Math.max(maximum * 2, 20);
        if (group.getRuleVersionEntity() != null && properties.getBacktest().isIncludeConfirmedPositiveSamples()) {
            for (ReviewIssueEntity issue : issueRepository.findByRuleVersionIdAndIssueStatusOrderByCreatedAtDesc(
                    group.getRuleVersionEntity().getId(), ReviewIssueStatus.CONFIRMED, PageRequest.of(0, querySize))) {
                BacktestSample sample = confirmedSample(issue);
                samples.putIfAbsent(sample.sampleId(), sample);
                if (samples.size() >= maximum) break;
            }
        }
        if (group.getRuleVersionEntity() != null && properties.getBacktest().isIncludeNormalSamples() && samples.size() < maximum) {
            for (ReviewRuleExecutionEntity execution : executionRepository
                    .findByRuleVersionIdAndMatchedOrderByCreatedAtDesc(
                            group.getRuleVersionEntity().getId(), false, PageRequest.of(0, querySize))) {
                BacktestSample sample = normalSample(execution);
                samples.putIfAbsent(sample.sampleId(), sample);
                if (samples.size() >= maximum) break;
            }
        }
        return samples.values().stream().limit(maximum).toList();
    }

    @Transactional(readOnly = true)
    public List<BacktestSample> semanticSamples(RuleFeedbackGovernanceGroupEntity group, int requestedMaximum) {
        int maximum = Math.max(1, Math.min(requestedMaximum,
                Math.min(properties.getBacktest().getMaximumSamples(), properties.getBacktest().getMaximumLlmSamples())));
        List<BacktestSample> all = collectByType(group, Math.max(maximum * 4, 20));
        List<BacktestSample> selected = new ArrayList<>();
        Set<String> selectedSamples = new LinkedHashSet<>();
        String primaryFeedbackType = group.isRuleGap() ? "FALSE_NEGATIVE" : "FALSE_POSITIVE";
        take(all, primaryFeedbackType, Math.min(3, maximum), selected, selectedSamples);
        take(all, "CONFIRMED_POSITIVE", 1, selected, selectedSamples);
        take(all, "NORMAL", 1, selected, selectedSamples);
        if (selected.size() < maximum) {
            for (BacktestSample sample : all) {
                if (selectedSamples.add(sample.sampleId())) selected.add(sample);
                if (selected.size() >= maximum) break;
            }
        }
        return List.copyOf(selected);
    }

    private List<BacktestSample> collectByType(RuleFeedbackGovernanceGroupEntity group, int querySize) {
        Map<String, BacktestSample> rows = new LinkedHashMap<>();
        groupService.feedbacks(group.getId()).forEach(feedback -> {
            BacktestSample sample = feedbackSample(feedback);
            rows.putIfAbsent(sample.sampleId(), sample);
        });
        if (group.getRuleVersionEntity() != null && properties.getBacktest().isIncludeConfirmedPositiveSamples()) {
            issueRepository.findByRuleVersionIdAndIssueStatusOrderByCreatedAtDesc(
                    group.getRuleVersionEntity().getId(), ReviewIssueStatus.CONFIRMED, PageRequest.of(0, querySize))
                    .forEach(issue -> {
                        BacktestSample sample = confirmedSample(issue);
                        rows.putIfAbsent(sample.sampleId(), sample);
                    });
        }
        if (group.getRuleVersionEntity() != null && properties.getBacktest().isIncludeNormalSamples()) {
            executionRepository.findByRuleVersionIdAndMatchedOrderByCreatedAtDesc(
                            group.getRuleVersionEntity().getId(), false, PageRequest.of(0, querySize))
                    .forEach(execution -> {
                        BacktestSample sample = normalSample(execution);
                        rows.putIfAbsent(sample.sampleId(), sample);
                    });
        }
        return List.copyOf(rows.values());
    }

    private void take(List<BacktestSample> all,
                      String type,
                      int count,
                      List<BacktestSample> selected,
                      Set<String> selectedSamples) {
        int taken = 0;
        for (BacktestSample sample : all) {
            if (!type.equals(sample.sampleType()) || !selectedSamples.add(sample.sampleId())) continue;
            selected.add(sample);
            if (++taken >= count) break;
        }
    }

    private BacktestSample feedbackSample(com.example.disclosurereview.persistence.entity.ReviewRuleFeedbackEntity feedback) {
        ReviewIssueEntity issue = feedback.getIssue();
        String sampleType = feedback.getFeedbackType() == null
                ? "FALSE_POSITIVE" : feedback.getFeedbackType().toUpperCase(java.util.Locale.ROOT);
        return new BacktestSample("feedback:" + feedback.getId(), feedback.getTask().getId(),
                feedback.getId(), issue == null ? null : issue.getId(), sampleType,
                "FALSE_POSITIVE".equals(sampleType),
                issue == null ? null : issue.getPageNumber(),
                issue == null ? null : issue.getEvidenceText());
    }

    private BacktestSample confirmedSample(ReviewIssueEntity issue) {
        return new BacktestSample("issue:" + issue.getId(), issue.getTask().getId(),
                null, issue.getId(), "CONFIRMED_POSITIVE", true,
                issue.getPageNumber(), issue.getEvidenceText());
    }

    private BacktestSample normalSample(ReviewRuleExecutionEntity execution) {
        return new BacktestSample("execution:" + execution.getId(), execution.getTask().getId(),
                null, null, "NORMAL", false, null, null);
    }

    public record BacktestSample(String sampleId,
                                 Long taskId,
                                 Long feedbackId,
                                 Long issueId,
                                 String sampleType,
                                 boolean oldRuleMatched,
                                 Integer targetPageNumber,
                                 String targetEvidenceText) {
        public BacktestSample(Long taskId, String sampleType, boolean oldRuleMatched) {
            this("task:" + taskId, taskId, null, null, sampleType, oldRuleMatched, null, null);
        }
    }
}
