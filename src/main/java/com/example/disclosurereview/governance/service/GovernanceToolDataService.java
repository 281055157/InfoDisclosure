package com.example.disclosurereview.governance.service;

import com.example.disclosurereview.config.FeedbackGovernanceProperties;
import com.example.disclosurereview.governance.domain.*;
import com.example.disclosurereview.governance.persistence.entity.*;
import com.example.disclosurereview.governance.persistence.repository.*;
import com.example.disclosurereview.persistence.entity.*;
import com.example.disclosurereview.persistence.repository.*;
import com.example.disclosurereview.pipeline.ReviewTaskContextStore;
import com.example.disclosurereview.rule.executor.RuleExecutorRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class GovernanceToolDataService {
    private final RuleFeedbackGovernanceGroupJpaRepository groupRepository;
    private final RuleChangeProposalJpaRepository proposalRepository;
    private final FeedbackGovernanceGroupService groupService;
    private final ReviewRuleDefinitionJpaRepository definitionRepository;
    private final ReviewRuleVersionJpaRepository versionRepository;
    private final ReviewRuleExecutionJpaRepository executionRepository;
    private final ReviewTaskJpaRepository taskRepository;
    private final DocumentPageJpaRepository pageRepository;
    private final ReviewTaskContextStore contextStore;
    private final GovernanceMemoryService memoryService;
    private final RuleSnapshotService snapshotService;
    private final GovernanceJsonService jsonService;
    private final FeedbackGovernanceProperties properties;
    private final RuleExecutorRegistry executorRegistry;
    private final ObjectMapper mapper;

    public GovernanceToolDataService(RuleFeedbackGovernanceGroupJpaRepository groupRepository,
                                     RuleChangeProposalJpaRepository proposalRepository,
                                     FeedbackGovernanceGroupService groupService,
                                     ReviewRuleDefinitionJpaRepository definitionRepository,
                                     ReviewRuleVersionJpaRepository versionRepository,
                                     ReviewRuleExecutionJpaRepository executionRepository,
                                     ReviewTaskJpaRepository taskRepository,
                                     DocumentPageJpaRepository pageRepository,
                                     ReviewTaskContextStore contextStore,
                                     GovernanceMemoryService memoryService,
                                     RuleSnapshotService snapshotService,
                                     GovernanceJsonService jsonService,
                                     FeedbackGovernanceProperties properties,
                                     RuleExecutorRegistry executorRegistry,
                                     ObjectMapper mapper) {
        this.groupRepository = groupRepository;
        this.proposalRepository = proposalRepository;
        this.groupService = groupService;
        this.definitionRepository = definitionRepository;
        this.versionRepository = versionRepository;
        this.executionRepository = executionRepository;
        this.taskRepository = taskRepository;
        this.pageRepository = pageRepository;
        this.contextStore = contextStore;
        this.memoryService = memoryService;
        this.snapshotService = snapshotService;
        this.jsonService = jsonService;
        this.properties = properties;
        this.executorRegistry = executorRegistry;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public ObjectNode group(Long id) {
        RuleFeedbackGovernanceGroupEntity group = getGroup(id);
        ObjectNode node = mapper.createObjectNode();
        node.put("groupId", group.getId());
        node.put("groupKey", group.getGroupKey());
        node.put("governanceIntent", group.getGovernanceIntent().name());
        node.put("sourceRuleAvailable", group.getRuleVersionEntity() != null);
        node.put("ruleCode", group.getRuleCode());
        if (group.getRuleVersionEntity() == null) node.putNull("ruleVersionId");
        else node.put("ruleVersionId", group.getRuleVersionEntity().getId());
        node.put("ruleVersion", group.getRuleVersion());
        if (group.getRuleVersionEntity() == null) node.putNull("ruleVersionNumber");
        else node.put("ruleVersionNumber", group.getRuleVersionEntity().getVersionNumber());
        node.put("feedbackType", group.getFeedbackType());
        node.put("documentCategory", group.getDocumentCategory());
        node.put("declaredFileType", group.getDeclaredFileType());
        node.put("productSeries", group.getProductSeries());
        node.put("issueType", group.getIssueType());
        node.put("feedbackCount", group.getFeedbackCount());
        node.put("status", group.getStatus().name());
        node.put("latestFeedbackAt", text(group.getLatestFeedbackAt()));
        ArrayNode comments = node.putArray("humanCommentSummary");
        groupService.feedbacks(id).stream().map(ReviewRuleFeedbackEntity::getComment)
                .filter(value -> value != null && !value.isBlank()).distinct().limit(5).forEach(comments::add);
        return node;
    }

    /**
     * Bounded, read-only evidence bundle for the first Agent turn. It avoids a
     * long sequence of individual discovery calls before a candidate can be
     * assessed, while retaining the same server-side access controls.
     */
    @Transactional(readOnly = true)
    public ObjectNode analysisBrief(Long groupId) {
        ObjectNode result = mapper.createObjectNode();
        ObjectNode group = group(groupId);
        result.set("group", group);
        result.set("feedbackSamples", feedbackSamples(groupId, properties.getMaximumSamplesPerGroup()));
        if (group.path("sourceRuleAvailable").asBoolean(false)) {
            String ruleCode = group.path("ruleCode").asText();
            int versionNumber = group.path("ruleVersionNumber").asInt();
            result.set("ruleDefinition", ruleDefinition(ruleCode));
            result.set("sourceRuleVersion", ruleVersion(ruleCode, versionNumber));
            List<Long> taskIds = groupService.feedbacks(groupId).stream()
                    .map(feedback -> feedback.getTask().getId()).filter(Objects::nonNull).distinct().limit(100).toList();
            result.set("executionRecords", executions(ruleCode, versionNumber, taskIds));
            result.set("historicalDecisions", memories(ruleCode, group.path("documentCategory").asText(null),
                    group.path("declaredFileType").asText(null), null, 10));
            result.set("similarAcceptedProposals", similarProposals(ruleCode, true));
            result.set("similarRejectedProposals", similarProposals(ruleCode, false));
        } else {
            result.putNull("ruleDefinition");
            result.putNull("sourceRuleVersion");
            result.set("executionRecords", mapper.createArrayNode());
            result.set("historicalDecisions", mapper.createArrayNode());
            result.set("similarAcceptedProposals", mapper.createArrayNode());
            result.set("similarRejectedProposals", mapper.createArrayNode());
            result.put("governanceGuidance", "RULE_GAP 分组没有来源规则；应基于漏报证据创建新规则，不得调用源规则版本相关 Tool");
        }
        result.set("availableExecutorSchemas", mapper.valueToTree(executorRegistry.schemas()));
        return result;
    }

    @Transactional(readOnly = true)
    public ArrayNode feedbackSamples(Long groupId, int limit) {
        ArrayNode rows = mapper.createArrayNode();
        groupService.feedbacks(groupId).stream().limit(Math.max(1, Math.min(limit, 100))).forEach(feedback -> {
            ReviewIssueEntity issue = feedback.getIssue();
            ObjectNode row = rows.addObject();
            row.put("feedbackId", feedback.getId());
            row.put("taskId", feedback.getTask().getId());
            row.put("taskNo", feedback.getTask().getTaskNo());
            row.put("issueId", issue == null ? null : issue.getId());
            row.put("issueType", issue == null ? null : issue.getIssueCode());
            row.put("issueDescription", issue == null ? null : issue.getExplanation());
            row.put("evidencePage", issue == null ? null : issue.getPageNumber());
            row.put("evidenceText", issue == null ? null : issue.getEvidenceText());
            row.put("ruleCode", feedback.getRuleCode());
            row.put("ruleVersionId", feedback.getRuleVersionId());
            row.put("documentCategory", feedback.getDocumentCategory());
            row.put("declaredFileType", feedback.getDeclaredDocumentType());
            row.put("productCode", feedback.getDeclaredProductCode());
            row.put("falsePositiveReason", feedback.getComment());
            row.set("issueSnapshot", parse(feedback.getIssueSnapshotJson()));
            row.set("manualSnapshot", parse(feedback.getManualSnapshotJson()));
        });
        return rows;
    }

    @Transactional(readOnly = true)
    public ObjectNode ruleDefinition(String ruleCode) {
        ReviewRuleDefinitionEntity rule = definitionRepository.findByRuleCode(ruleCode)
                .orElseThrow(() -> new IllegalArgumentException("规则不存在: " + ruleCode));
        ObjectNode node = mapper.createObjectNode();
        node.put("id", rule.getId()); node.put("ruleCode", rule.getRuleCode());
        node.put("ruleName", rule.getRuleName()); node.put("enabled", rule.isEnabled());
        node.put("priority", rule.getPriority()); node.put("activeVersionId", rule.getActiveVersionId());
        node.put("severity", rule.getSeverity()); node.put("confidence", rule.getConfidence());
        return node;
    }

    @Transactional(readOnly = true)
    public ObjectNode ruleVersion(String ruleCode, int versionNumber) {
        ReviewRuleDefinitionEntity definition = definitionRepository.findByRuleCode(ruleCode)
                .orElseThrow(() -> new IllegalArgumentException("规则不存在: " + ruleCode));
        ReviewRuleVersionEntity version = versionRepository.findByRuleDefinition_IdOrderByVersionNumberDesc(definition.getId())
                .stream().filter(row -> Objects.equals(row.getVersionNumber(), versionNumber)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("规则版本不存在: " + ruleCode + " v" + versionNumber));
        return snapshotService.snapshot(definition, version);
    }

    @Transactional(readOnly = true)
    public ArrayNode executions(String ruleCode, int versionNumber, List<Long> taskIds) {
        ReviewRuleDefinitionEntity definition = definitionRepository.findByRuleCode(ruleCode)
                .orElseThrow(() -> new IllegalArgumentException("规则不存在: " + ruleCode));
        ReviewRuleVersionEntity version = versionRepository.findByRuleDefinition_IdOrderByVersionNumberDesc(definition.getId())
                .stream().filter(row -> Objects.equals(row.getVersionNumber(), versionNumber)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("规则版本不存在"));
        ArrayNode result = mapper.createArrayNode();
        executionRepository.findByRuleVersionIdAndTask_IdInOrderByCreatedAtDesc(version.getId(), taskIds).forEach(row -> {
            ObjectNode node = result.addObject();
            node.put("executionId", row.getId()); node.put("taskId", row.getTask().getId());
            node.put("status", row.getExecutionStatus()); node.put("matched", row.isMatched());
            node.put("issueCount", row.getIssueCount()); node.put("durationMs", row.getDurationMs());
            node.put("error", row.getErrorMessage()); node.set("input", parse(row.getInputSnapshotJson()));
            node.set("result", parse(row.getResultJson())); node.set("evidence", parse(row.getEvidenceJson()));
        });
        return result;
    }

    @Transactional(readOnly = true)
    public ObjectNode documentContext(Long taskId, int pageNumber, boolean adjacent) {
        ReviewTaskEntity task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + taskId));
        ObjectNode node = mapper.createObjectNode();
        node.put("taskId", taskId); node.put("fileName", task.getOriginalFileName());
        node.put("documentCategory", task.getDocumentCategory() == null ? null : task.getDocumentCategory().name());
        node.put("declaredFileType", task.getDeclaredDocumentType());
        node.put("declaredProductCode", task.getDeclaredProductCode()); node.put("b9Value", task.getB9Value());
        JsonNode taskContext = contextStore.load(taskId);
        node.set("productMatch", taskContext.path("productMatch"));
        node.set("ruleReview", taskContext.path("ruleReview"));
        ArrayNode pages = node.putArray("pages");
        int from = adjacent ? Math.max(1, pageNumber - 1) : pageNumber;
        int to = adjacent ? pageNumber + 1 : pageNumber;
        int remaining = properties.getAgent().getMaximumDocumentContextChars();
        for (DocumentPageEntity page : pageRepository.findByTaskIdOrderByPageNumber(taskId)) {
            if (page.getPageNumber() < from || page.getPageNumber() > to || remaining <= 0) continue;
            String text = page.getRawText() == null ? "" : page.getRawText();
            if (text.length() > remaining) text = text.substring(0, remaining);
            ObjectNode p = pages.addObject(); p.put("pageNumber", page.getPageNumber()); p.put("text", text);
            remaining -= text.length();
        }
        node.put("truncated", remaining <= 0);
        return node;
    }

    @Transactional(readOnly = true)
    public ArrayNode memories(String ruleCode, String category, String fileType, RootCauseType rootCause, int limit) {
        ArrayNode rows = mapper.createArrayNode();
        memoryService.search(ruleCode, category, fileType, rootCause, limit).forEach(memory -> {
            ObjectNode node = rows.addObject(); node.put("memoryId", memory.getId());
            node.put("type", memory.getMemoryType().name()); node.put("decision", memory.getDecision().name());
            node.put("proposalType", memory.getProposalType() == null ? null : memory.getProposalType().name());
            node.put("decisionReason", memory.getDecisionReason()); node.put("humanComment", memory.getHumanComment());
            node.put("caseSummary", memory.getCaseSummary()); node.put("agentSuggestion", memory.getAgentSuggestionSummary());
            node.put("finalChange", memory.getFinalChangeSummary()); node.set("effect", parse(memory.getEffectSummaryJson()));
        });
        return rows;
    }

    @Transactional(readOnly = true)
    public ArrayNode similarProposals(String ruleCode, boolean accepted) {
        List<ProposalStatus> statuses = accepted
                ? List.of(ProposalStatus.APPROVED, ProposalStatus.APPROVED_WITH_MODIFICATION, ProposalStatus.APPLIED)
                : List.of(ProposalStatus.REJECTED);
        ArrayNode rows = mapper.createArrayNode();
        proposalRepository.findByRuleCodeAndProposalStatusInOrderByCreatedAtDesc(ruleCode, statuses)
                .stream().limit(10).forEach(proposal -> {
                    ObjectNode node = rows.addObject(); node.put("proposalId", proposal.getId());
                    node.put("proposalNo", proposal.getProposalNo()); node.put("type", proposal.getProposalType().name());
                    node.put("status", proposal.getProposalStatus().name()); node.put("rootCause", proposal.getRootCauseType().name());
                    node.put("summary", proposal.getProblemSummary()); node.put("reviewComment", proposal.getReviewComment());
                    node.put("rejectionReason", proposal.getRejectionReason());
                });
        return rows;
    }

    @Transactional(readOnly = true)
    public ObjectNode compare(Long sourceVersionId, RuleCandidate candidate) {
        ReviewRuleVersionEntity source = versionRepository.findById(sourceVersionId)
                .orElseThrow(() -> new IllegalArgumentException("源规则版本不存在"));
        ObjectNode result = mapper.createObjectNode();
        result.set("before", snapshotService.snapshot(source.getRuleDefinition(), source));
        result.set("after", mapper.valueToTree(candidate));
        result.put("executorTypeChanged", !Objects.equals(source.getExecutorType(), candidate.executorType().name()));
        result.put("scopeChanged", !canonical(parse(source.getScopeJson())).equals(canonical(candidate.scope())));
        result.put("conditionChanged", !canonical(parse(source.getConditionJson())).equals(canonical(candidate.condition())));
        result.put("promptChanged", !canonical(parse(source.getPromptJson())).equals(canonical(candidate.prompt())));
        return result;
    }

    @Transactional(readOnly = true)
    public ObjectNode estimate(Long groupId, RuleCandidate candidate) {
        RuleFeedbackGovernanceGroupEntity group = getGroup(groupId);
        Instant since = Instant.now().minus(properties.getLookbackDays(), ChronoUnit.DAYS);
        ObjectNode result = mapper.createObjectNode();
        if (group.getRuleVersionEntity() == null) {
            result.putNull("ruleVersionId");
            result.put("sourceRuleAvailable", false);
            result.put("historicalExecutionCount", 0);
            result.put("historicalHitCount", 0);
            result.put("feedbackDocumentCount", groupService.feedbacks(groupId).stream()
                    .map(feedback -> feedback.getTask().getId()).filter(Objects::nonNull).distinct().count());
        } else {
            result.put("ruleVersionId", group.getRuleVersionEntity().getId());
            result.put("sourceRuleAvailable", true);
            result.put("historicalExecutionCount", executionRepository.countByRuleVersionIdAndCreatedAtAfter(group.getRuleVersionEntity().getId(), since));
            result.put("historicalHitCount", executionRepository.countByRuleVersionIdAndMatchedTrueAndCreatedAtAfter(group.getRuleVersionEntity().getId(), since));
        }
        result.put("documentCategory", group.getDocumentCategory()); result.put("declaredFileType", group.getDeclaredFileType());
        result.put("candidateHash", jsonService.hash(candidate));
        result.set("candidateScope", candidate.scope());
        return result;
    }

    private RuleFeedbackGovernanceGroupEntity getGroup(Long id) {
        return groupRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("治理分组不存在: " + id));
    }
    private JsonNode parse(String value) { try { return mapper.readTree(value == null || value.isBlank() ? "{}" : value); } catch (Exception e) { return mapper.createObjectNode(); } }
    private String canonical(JsonNode node) { try { return mapper.writeValueAsString(node); } catch (Exception e) { return "{}"; } }
    private String text(Instant value) { return value == null ? null : value.toString(); }
}
