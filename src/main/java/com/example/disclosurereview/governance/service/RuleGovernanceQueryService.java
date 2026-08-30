package com.example.disclosurereview.governance.service;

import com.example.disclosurereview.governance.domain.*;
import com.example.disclosurereview.governance.dto.RuleGovernanceDtos.*;
import com.example.disclosurereview.governance.persistence.entity.*;
import com.example.disclosurereview.governance.persistence.repository.*;
import com.example.disclosurereview.persistence.entity.AuditLogEntity;
import com.example.disclosurereview.persistence.entity.ReviewIssueEntity;
import com.example.disclosurereview.persistence.entity.ReviewRuleFeedbackEntity;
import com.example.disclosurereview.service.AuditLogService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;

@Service
public class RuleGovernanceQueryService {
    private final RuleGovernanceRunJpaRepository runRepository;
    private final RuleFeedbackGovernanceGroupJpaRepository groupRepository;
    private final RuleChangeProposalJpaRepository proposalRepository;
    private final RuleChangeProposalFeedbackJpaRepository proposalFeedbackRepository;
    private final RuleChangeProposalActionJpaRepository actionRepository;
    private final RuleGovernanceMemoryJpaRepository memoryRepository;
    private final RuleGovernanceToolCallJpaRepository toolCallRepository;
    private final FeedbackGovernanceGroupService groupService;
    private final GovernanceMemoryService memoryService;
    private final AuditLogService auditLogService;
    private final ObjectMapper mapper;

    public RuleGovernanceQueryService(RuleGovernanceRunJpaRepository runRepository,
                                      RuleFeedbackGovernanceGroupJpaRepository groupRepository,
                                      RuleChangeProposalJpaRepository proposalRepository,
                                      RuleChangeProposalFeedbackJpaRepository proposalFeedbackRepository,
                                      RuleChangeProposalActionJpaRepository actionRepository,
                                      RuleGovernanceMemoryJpaRepository memoryRepository,
                                      RuleGovernanceToolCallJpaRepository toolCallRepository,
                                      FeedbackGovernanceGroupService groupService,
                                      GovernanceMemoryService memoryService,
                                      AuditLogService auditLogService,
                                      ObjectMapper mapper) {
        this.runRepository = runRepository; this.groupRepository = groupRepository;
        this.proposalRepository = proposalRepository; this.proposalFeedbackRepository = proposalFeedbackRepository;
        this.actionRepository = actionRepository;
        this.memoryRepository = memoryRepository; this.toolCallRepository = toolCallRepository;
        this.groupService = groupService; this.memoryService = memoryService;
        this.auditLogService = auditLogService; this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<RunResponse> runs() { return runRepository.findAllByOrderByCreatedAtDesc().stream().map(this::run).toList(); }

    @Transactional(readOnly = true)
    public RunResponse run(Long id) { return run(runRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("治理运行不存在"))); }

    @Transactional(readOnly = true)
    public List<GroupResponse> groups(String status, String ruleCode, String documentCategory) {
        return groupRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(row -> !StringUtils.hasText(status) || row.getStatus().name().equalsIgnoreCase(status))
                .filter(row -> !StringUtils.hasText(ruleCode)
                        || (row.getRuleCode() != null && row.getRuleCode().contains(ruleCode.strip()))
                        || (row.getIssueType() != null && row.getIssueType().contains(ruleCode.strip())))
                .filter(row -> !StringUtils.hasText(documentCategory) || documentCategory.equalsIgnoreCase(row.getDocumentCategory()))
                .map(this::group).toList();
    }

    @Transactional(readOnly = true)
    public GroupResponse group(Long id) { return group(groupRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("治理分组不存在"))); }

    @Transactional(readOnly = true)
    public List<FeedbackSampleResponse> feedbacks(Long groupId) { return groupService.feedbacks(groupId).stream().map(this::feedback).toList(); }

    @Transactional(readOnly = true)
    public List<ProposalSummaryResponse> proposals(String status, String type, String rootCause, String ruleCode) {
        return proposalRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(row -> !StringUtils.hasText(status) || row.getProposalStatus().name().equalsIgnoreCase(status))
                .filter(row -> !StringUtils.hasText(type) || row.getProposalType().name().equalsIgnoreCase(type))
                .filter(row -> !StringUtils.hasText(rootCause) || row.getRootCauseType().name().equalsIgnoreCase(rootCause))
                .filter(row -> !StringUtils.hasText(ruleCode) || (row.getRuleCode() != null && row.getRuleCode().contains(ruleCode.strip())))
                .map(this::proposalSummary).toList();
    }

    @Transactional(readOnly = true)
    public ProposalDetailResponse proposal(Long id) {
        RuleChangeProposalEntity entity = proposalRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("治理提案不存在: " + id));
        return new ProposalDetailResponse(proposalSummary(entity), group(entity.getGovernanceGroup()),
                entity.getProblemSummary(), entity.getRootCauseAnalysis(), entity.getChangeReason(),
                entity.getExpectedEffect(), entity.getRiskDescription(), parse(entity.getBeforeRuleSnapshotJson()),
                parse(entity.getAfterRuleSnapshotJson()), parse(entity.getFinalRuleSnapshotJson()),
                parse(entity.getValidationResultJson()), parse(entity.getBacktestResultJson()), parse(entity.getAffectedScopeJson()),
                entity.getOptimizationCategory(), entity.getOptimizationAdvice(), entity.getResponsibleModule(),
                entity.getProposalPriority(), entity.isHumanFollowUpRequired(), entity.getAgentProvider(),
                entity.getAgentModel(), entity.getAgentPromptVersion(),
                entity.getDraftRuleDefinition() == null ? null : entity.getDraftRuleDefinition().getId(),
                entity.getDraftRuleVersion() == null ? null : entity.getDraftRuleVersion().getId(),
                entity.getReviewComment(), entity.getRejectionReason(), entity.getDeferReason(), entity.getDeferredUntil(),
                actionRepository.findByProposal_IdOrderBySequenceNoAsc(id).stream().map(this::proposalAction).toList(),
                proposalFeedbackRepository.findByProposal_IdOrderByFeedback_CreatedAtDesc(id).stream()
                        .map(link -> feedback(link.getFeedback())).toList(),
                memoryRepository.findByProposal_IdOrderByCreatedAtDesc(id).stream().map(this::memory).toList(),
                toolCallRepository.findByGovernanceGroup_IdOrderById(entity.getGovernanceGroup().getId()).stream().map(this::toolCall).toList(),
                auditLogService.proposalTimeline(id).stream().map(this::audit).toList());
    }

    @Transactional(readOnly = true)
    public JsonNode diff(Long id) {
        RuleChangeProposalEntity entity = proposalRepository.findById(id).orElseThrow();
        var node = mapper.createObjectNode(); node.set("before", parse(entity.getBeforeRuleSnapshotJson()));
        node.set("after", parse(StringUtils.hasText(entity.getFinalRuleSnapshotJson()) ? entity.getFinalRuleSnapshotJson() : entity.getAfterRuleSnapshotJson()));
        var actions = node.putArray("actions");
        actionRepository.findByProposal_IdOrderBySequenceNoAsc(id).forEach(action -> {
            var row = actions.addObject();
            row.put("sequenceNo", action.getSequenceNo());
            row.put("actionType", action.getActionType().name());
            row.put("actionStatus", action.getActionStatus().name());
            row.set("before", parse(action.getBeforeRuleSnapshotJson()));
            row.set("after", parse(action.getAfterRuleSnapshotJson()));
        });
        return node;
    }

    @Transactional(readOnly = true)
    public JsonNode backtest(Long id) { return parse(proposalRepository.findById(id).orElseThrow().getBacktestResultJson()); }

    @Transactional(readOnly = true)
    public List<MemoryResponse> memories(String ruleCode, String category, String fileType, String rootCause) {
        RootCauseType root = StringUtils.hasText(rootCause) ? RootCauseType.valueOf(rootCause) : null;
        return memoryService.search(ruleCode, category, fileType, root, 50).stream().map(this::memory).toList();
    }

    @Transactional(readOnly = true)
    public MemoryResponse memory(Long id) { return memory(memoryRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Memory 不存在"))); }

    private RunResponse run(RuleGovernanceRunEntity row) { return new RunResponse(row.getId(), row.getRunNo(), row.getTriggerType(), row.getStatus(), row.getStartedAt(), row.getFinishedAt(), row.getScannedFeedbackCount(), row.getCreatedGroupCount(), row.getCreatedProposalCount(), row.getFailedGroupCount(), row.getSkippedFeedbackCount(), row.getSkipReasonSummary(), row.getModelConfig() == null ? null : row.getModelConfig().getId(), row.getInputTokenCount(), row.getOutputTokenCount(), row.getCacheHitTokenCount(), row.getDurationMs(), row.getErrorMessage(), row.getCreatedAt()); }
    private GroupResponse group(RuleFeedbackGovernanceGroupEntity row) { return new GroupResponse(row.getId(), row.getGroupKey(), row.getGovernanceIntent(), row.getFeedbackType(), row.getRuleCode(), row.getRuleDefinition() == null ? null : row.getRuleDefinition().getRuleName(), row.getRuleVersionEntity() == null ? null : row.getRuleVersionEntity().getId(), row.getRuleVersion(), row.getDocumentCategory(), row.getDeclaredFileType(), row.getProductSeries(), row.getIssueType(), row.getFeedbackCount(), row.getStatus(), !proposalRepository.findByGovernanceGroup_IdOrderByCreatedAtDesc(row.getId()).isEmpty(), row.getLatestFeedbackAt(), row.getGovernanceRun().getId(), row.getErrorMessage(), row.getCreatedAt()); }
    private ProposalSummaryResponse proposalSummary(RuleChangeProposalEntity row) { return new ProposalSummaryResponse(row.getId(), row.getProposalNo(), row.getProposalType(), row.getProposalStatus(), row.getRuleCode(), row.getRuleDefinition() == null ? null : row.getRuleDefinition().getRuleName(), row.getSourceRuleVersion(), row.getRootCauseType(), (int) proposalFeedbackRepository.countByProposal_Id(row.getId()), row.getAgentConfidence(), parse(row.getBacktestResultJson()).path("riskLevel").asText(null), row.getGovernanceGroup().getDocumentCategory(), row.getGovernanceGroup().getDeclaredFileType(), row.getAgentModel(), row.getReviewedBy(), row.getCreatedAt(), row.getReviewedAt(), actionRepository.countByProposal_Id(row.getId())); }
    private ProposalActionResponse proposalAction(RuleChangeProposalActionEntity row) { return new ProposalActionResponse(row.getId(), row.getSequenceNo(), row.getActionType(), row.getActionStatus().name(), row.getRuleCode(), row.getSourceRuleVersion() == null ? null : row.getSourceRuleVersion().getId(), parse(row.getBeforeRuleSnapshotJson()), parse(row.getAfterRuleSnapshotJson()), parse(row.getValidationResultJson()), parse(row.getBacktestResultJson()), parse(row.getAffectedScopeJson()), row.getDraftRuleDefinition() == null ? null : row.getDraftRuleDefinition().getId(), row.getDraftRuleVersion() == null ? null : row.getDraftRuleVersion().getId()); }
    private FeedbackSampleResponse feedback(ReviewRuleFeedbackEntity row) { ReviewIssueEntity issue = row.getIssue(); return new FeedbackSampleResponse(row.getId(), row.getTask().getId(), row.getTask().getTaskNo(), issue == null ? null : issue.getId(), issue == null ? null : issue.getIssueCode(), issue == null ? null : issue.getExplanation(), issue == null ? null : issue.getPageNumber(), issue == null ? null : issue.getEvidenceText(), row.getRuleCode(), row.getRuleVersionId(), row.getDocumentCategory(), row.getDeclaredDocumentType(), row.getDeclaredProductCode(), row.getComment(), parse(row.getIssueSnapshotJson()), parse(row.getManualSnapshotJson()), row.getReviewer(), row.getCreatedAt()); }
    private MemoryResponse memory(RuleGovernanceMemoryEntity row) { return new MemoryResponse(row.getId(), row.getMemoryType(), row.getRuleCode(), row.getRuleVersion(), row.getDocumentCategory(), row.getDeclaredFileType(), row.getRootCauseType(), row.getProposalType(), row.getProposal() == null ? null : row.getProposal().getId(), row.getDecision(), row.getDecisionReason(), row.getHumanComment(), row.getCaseSummary(), row.getAgentSuggestionSummary(), row.getFinalChangeSummary(), parse(row.getBacktestSummaryJson()), parse(row.getEffectSummaryJson()), row.getCreatedAt()); }
    private ToolCallResponse toolCall(RuleGovernanceToolCallEntity row) { return new ToolCallResponse(row.getId(), row.getIterationNumber(), row.getToolName(), row.getCallStatus(), row.getCandidateHash(), row.getDurationMs(), row.getErrorMessage(), row.getCreatedAt()); }
    private GovernanceAuditResponse audit(AuditLogEntity row) { return new GovernanceAuditResponse(row.getId(), row.getOperationType(), row.getOperator(), row.getOperationDetail(), row.getBeforeValue(), row.getAfterValue(), row.getCreatedAt()); }
    private JsonNode parse(String json) { try { return mapper.readTree(StringUtils.hasText(json) ? json : "{}"); } catch (Exception e) { return mapper.createObjectNode(); } }
}
