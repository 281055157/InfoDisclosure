package com.example.disclosurereview.governance.service;

import com.example.disclosurereview.config.FeedbackGovernanceProperties;
import com.example.disclosurereview.governance.domain.*;
import com.example.disclosurereview.governance.persistence.entity.*;
import com.example.disclosurereview.governance.persistence.repository.*;
import com.example.disclosurereview.persistence.entity.ReviewRuleFeedbackEntity;
import com.example.disclosurereview.persistence.entity.ReviewRuleVersionEntity;
import com.example.disclosurereview.persistence.repository.ReviewRuleFeedbackJpaRepository;
import com.example.disclosurereview.persistence.repository.ReviewRuleVersionJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class FeedbackGovernanceGroupService {
    private static final List<GovernanceGroupStatus> ACTIVE_GROUP_STATUSES = List.of(
            GovernanceGroupStatus.PENDING, GovernanceGroupStatus.ANALYZING,
            GovernanceGroupStatus.PROPOSAL_CREATED, GovernanceGroupStatus.DEFERRED,
            GovernanceGroupStatus.FAILED);

    private final FeedbackGovernanceProperties properties;
    private final ReviewRuleFeedbackJpaRepository feedbackRepository;
    private final ReviewRuleVersionJpaRepository versionRepository;
    private final RuleFeedbackGovernanceGroupJpaRepository groupRepository;
    private final RuleFeedbackGovernanceGroupItemJpaRepository itemRepository;
    private final GovernanceGroupKeyService keyService;

    public FeedbackGovernanceGroupService(FeedbackGovernanceProperties properties,
                                          ReviewRuleFeedbackJpaRepository feedbackRepository,
                                          ReviewRuleVersionJpaRepository versionRepository,
                                          RuleFeedbackGovernanceGroupJpaRepository groupRepository,
                                          RuleFeedbackGovernanceGroupItemJpaRepository itemRepository,
                                          GovernanceGroupKeyService keyService) {
        this.properties = properties;
        this.feedbackRepository = feedbackRepository;
        this.versionRepository = versionRepository;
        this.groupRepository = groupRepository;
        this.itemRepository = itemRepository;
        this.keyService = keyService;
    }

    @Transactional
    public GroupingResult createGroups(RuleGovernanceRunEntity run) {
        Instant createdAfter = Instant.now().minus(properties.getLookbackDays(), ChronoUnit.DAYS);
        List<ReviewRuleFeedbackEntity> scanned = feedbackRepository
                .findByProcessStatusInAndCreatedAtAfterOrderByCreatedAtAsc(
                        FeedbackGovernanceStatus.PENDING_DATABASE_VALUES, createdAfter)
                .stream()
                .filter(row -> !itemRepository.existsByFeedback_Id(row.getId()))
                .toList();

        Map<String, List<ReviewRuleFeedbackEntity>> grouped = new LinkedHashMap<>();
        Map<String, Integer> skippedReasons = new LinkedHashMap<>();
        for (ReviewRuleFeedbackEntity row : scanned) {
            if (isFalsePositive(row)) {
                if (!hasSourceRule(row)) {
                    recordSkipped(skippedReasons, "FALSE_POSITIVE_RULE_REFERENCE_MISSING", 1);
                    continue;
                }
            } else if (isFalseNegative(row)) {
                if (!org.springframework.util.StringUtils.hasText(keyService.issueType(row))) {
                    recordSkipped(skippedReasons, "FALSE_NEGATIVE_ISSUE_TYPE_MISSING", 1);
                    continue;
                }
            } else {
                recordSkipped(skippedReasons, "UNSUPPORTED_FEEDBACK_TYPE", 1);
                continue;
            }
            grouped.computeIfAbsent(keyService.key(row), ignored -> new ArrayList<>()).add(row);
        }

        List<RuleFeedbackGovernanceGroupEntity> created = new ArrayList<>();
        for (Map.Entry<String, List<ReviewRuleFeedbackEntity>> entry : grouped.entrySet()) {
            List<ReviewRuleFeedbackEntity> rows = entry.getValue();
            if (created.size() >= properties.getMaximumGroupsPerRun()) {
                recordSkipped(skippedReasons, "MAXIMUM_GROUPS_REACHED", rows.size());
                continue;
            }
            if (rows.size() < properties.getMinimumFeedbackCount()) {
                recordSkipped(skippedReasons, "FEEDBACK_COUNT_BELOW_THRESHOLD", rows.size());
                continue;
            }
            ReviewRuleFeedbackEntity representative = rows.get(rows.size() - 1);
            GovernanceIntent intent = isFalseNegative(representative)
                    ? GovernanceIntent.RULE_GAP : GovernanceIntent.RULE_CORRECTION;
            ReviewRuleVersionEntity version = null;
            if (intent == GovernanceIntent.RULE_CORRECTION) {
                Optional<ReviewRuleVersionEntity> resolved = versionRepository.findById(representative.getRuleVersionId());
                if (resolved.isEmpty() || resolved.get().getRuleDefinition() == null) {
                    recordSkipped(skippedReasons, "RULE_VERSION_NOT_FOUND", rows.size());
                    continue;
                }
                if (!representative.getRuleCode().equals(resolved.get().getRuleDefinition().getRuleCode())) {
                    // Do not send an ambiguous group to the Agent: it could propose a change for the wrong rule.
                    recordSkipped(skippedReasons, "RULE_CODE_VERSION_MISMATCH", rows.size());
                    continue;
                }
                version = resolved.get();
            }
            if (groupRepository.findFirstByGroupKeyAndStatusInOrderByCreatedAtDesc(entry.getKey(), ACTIVE_GROUP_STATUSES).isPresent()) {
                recordSkipped(skippedReasons, "ACTIVE_GROUP_ALREADY_EXISTS", rows.size());
                continue;
            }
            Instant now = Instant.now();
            RuleFeedbackGovernanceGroupEntity group = new RuleFeedbackGovernanceGroupEntity();
            group.setGroupKey(entry.getKey());
            group.setGovernanceIntent(intent);
            group.setRuleDefinition(version == null ? null : version.getRuleDefinition());
            group.setRuleCode(version == null ? null : representative.getRuleCode());
            group.setRuleVersionEntity(version);
            group.setRuleVersion(version == null ? null : version.getVersionCode());
            group.setFeedbackType(representative.getFeedbackType().toUpperCase(Locale.ROOT));
            group.setDocumentCategory(representative.getDocumentCategory());
            group.setDeclaredFileType(representative.getDeclaredDocumentType());
            group.setIssueType(keyService.issueType(representative));
            group.setStatus(GovernanceGroupStatus.PENDING);
            group.setFeedbackCount(rows.size());
            group.setRepresentativeFeedback(representative);
            group.setLatestFeedbackAt(rows.stream().map(ReviewRuleFeedbackEntity::getCreatedAt)
                    .filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(now));
            group.setGovernanceRun(run);
            group.setCreatedAt(now);
            group.setUpdatedAt(now);
            group = groupRepository.save(group);

            for (ReviewRuleFeedbackEntity feedback : rows) {
                RuleFeedbackGovernanceGroupItemEntity item = new RuleFeedbackGovernanceGroupItemEntity();
                item.setGroup(group);
                item.setFeedback(feedback);
                item.setCreatedAt(now);
                itemRepository.save(item);
                feedback.setProcessStatus(FeedbackGovernanceStatus.GROUPED.name());
                feedbackRepository.save(feedback);
            }
            created.add(group);
        }
        return new GroupingResult(scanned.size(), List.copyOf(created),
                skippedReasons.values().stream().mapToInt(Integer::intValue).sum(), Map.copyOf(skippedReasons));
    }

    @Transactional(readOnly = true)
    public List<ReviewRuleFeedbackEntity> feedbacks(Long groupId) {
        return itemRepository.findByGroup_IdOrderByFeedback_CreatedAtDesc(groupId)
                .stream().map(RuleFeedbackGovernanceGroupItemEntity::getFeedback).toList();
    }

    @Transactional(readOnly = true)
    public boolean containsTask(Long groupId, Long taskId) {
        return itemRepository.existsByGroup_IdAndFeedback_Task_Id(groupId, taskId);
    }

    private void recordSkipped(Map<String, Integer> reasons, String reason, int count) {
        reasons.merge(reason, count, Integer::sum);
    }

    private boolean isFalsePositive(ReviewRuleFeedbackEntity row) {
        return "FALSE_POSITIVE".equalsIgnoreCase(row.getFeedbackType());
    }

    private boolean isFalseNegative(ReviewRuleFeedbackEntity row) {
        return "FALSE_NEGATIVE".equalsIgnoreCase(row.getFeedbackType());
    }

    private boolean hasSourceRule(ReviewRuleFeedbackEntity row) {
        return org.springframework.util.StringUtils.hasText(row.getRuleCode()) && row.getRuleVersionId() != null;
    }

    public record GroupingResult(int scannedFeedbackCount,
                                 List<RuleFeedbackGovernanceGroupEntity> groups,
                                 int skippedFeedbackCount,
                                 Map<String, Integer> skippedReasons) {}
}
