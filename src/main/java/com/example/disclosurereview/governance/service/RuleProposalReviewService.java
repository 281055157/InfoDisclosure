package com.example.disclosurereview.governance.service;

import com.example.disclosurereview.governance.domain.*;
import com.example.disclosurereview.governance.persistence.entity.*;
import com.example.disclosurereview.governance.persistence.repository.*;
import com.example.disclosurereview.persistence.entity.ReviewRuleDefinitionEntity;
import com.example.disclosurereview.persistence.entity.ReviewRuleFeedbackEntity;
import com.example.disclosurereview.persistence.entity.ReviewRuleVersionEntity;
import com.example.disclosurereview.persistence.repository.*;
import com.example.disclosurereview.rule.domain.RuleExecutorType;
import com.example.disclosurereview.service.AuditLogService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;

@Service
public class RuleProposalReviewService {
    private final RuleChangeProposalJpaRepository proposalRepository;
    private final ReviewRuleDefinitionJpaRepository definitionRepository;
    private final ReviewRuleVersionJpaRepository versionRepository;
    private final RuleChangeProposalFeedbackJpaRepository proposalFeedbackRepository;
    private final RuleChangeProposalActionJpaRepository actionRepository;
    private final ReviewRuleFeedbackJpaRepository feedbackRepository;
    private final RuleFeedbackGovernanceGroupJpaRepository groupRepository;
    private final RuleCandidateValidationService validationService;
    private final RuleBacktestService backtestService;
    private final GovernanceMemoryService memoryService;
    private final GovernanceJsonService jsonService;
    private final AuditLogService auditLogService;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper mapper;

    public RuleProposalReviewService(RuleChangeProposalJpaRepository proposalRepository,
                                     ReviewRuleDefinitionJpaRepository definitionRepository,
                                     ReviewRuleVersionJpaRepository versionRepository,
                                     RuleChangeProposalFeedbackJpaRepository proposalFeedbackRepository,
                                     RuleChangeProposalActionJpaRepository actionRepository,
                                     ReviewRuleFeedbackJpaRepository feedbackRepository,
                                     RuleFeedbackGovernanceGroupJpaRepository groupRepository,
                                     RuleCandidateValidationService validationService,
                                     RuleBacktestService backtestService,
                                     GovernanceMemoryService memoryService,
                                     GovernanceJsonService jsonService,
                                     AuditLogService auditLogService,
                                     TransactionTemplate transactionTemplate,
                                     ObjectMapper mapper) {
        this.proposalRepository = proposalRepository; this.definitionRepository = definitionRepository;
        this.versionRepository = versionRepository; this.proposalFeedbackRepository = proposalFeedbackRepository;
        this.actionRepository = actionRepository;
        this.feedbackRepository = feedbackRepository; this.groupRepository = groupRepository;
        this.validationService = validationService; this.backtestService = backtestService;
        this.memoryService = memoryService; this.jsonService = jsonService; this.auditLogService = auditLogService;
        this.transactionTemplate = transactionTemplate; this.mapper = mapper;
    }

    @Transactional
    public RuleChangeProposalEntity approve(Long proposalId, String operator, String comment) {
        RuleChangeProposalEntity proposal = pending(proposalId);
        if (proposal.getProposalType() == ProposalType.COMPOSITE_RULE_CHANGE) {
            return acceptComposite(proposal, operator, comment);
        }
        RuleCandidate candidate = candidateFrom(proposal.getAfterRuleSnapshotJson());
        return accept(proposal, candidate, false, operator, comment,
                proposal.getValidationResultJson(), proposal.getBacktestResultJson());
    }

    public RuleChangeProposalEntity approveWithModification(Long proposalId,
                                                            JsonNode candidateNode,
                                                            String operator,
                                                            String comment) {
        ModificationContext snapshot = transactionTemplate.execute(status -> {
            RuleChangeProposalEntity proposal = proposalRepository.findById(proposalId)
                    .orElseThrow(() -> new IllegalArgumentException("提案不存在: " + proposalId));
            return new ModificationContext(proposal.getProposalStatus(), proposal.getProposalType(),
                    proposal.getGovernanceGroup().getId(), proposal.getGovernanceGroup().getRuleCode());
        });
        if (snapshot == null) throw new IllegalStateException("无法读取提案");
        if (snapshot.status() != ProposalStatus.PENDING_REVIEW) throw new IllegalStateException("提案已被处理");
        if (!requiresCandidate(snapshot.type())) throw new IllegalArgumentException("该提案类型不支持修改候选规则");
        RuleCandidate candidate = RuleCandidate.from(candidateNode, mapper);
        CandidateValidationResult validation = validationService.validate(candidate,
                snapshot.ruleCode(), snapshot.type() == ProposalType.CREATE_RULE);
        if (!validation.valid()) throw new IllegalArgumentException("人工候选规则校验失败: " + String.join("; ", validation.errors()));
        RuleBacktestResult backtest = backtestService.run(snapshot.groupId(), candidate, 100);
        backtestService.requireUsableForProposal(backtest, candidate);
        return transactionTemplate.execute(status -> {
            RuleChangeProposalEntity proposal = pending(proposalId);
            return accept(proposal, candidate, true, operator, comment,
                    jsonService.json(validation), jsonService.json(backtest));
        });
    }

    @Transactional
    public RuleChangeProposalEntity reject(Long proposalId,
                                           ProposalRejectionReason reason,
                                           String operator,
                                           String comment) {
        RuleChangeProposalEntity proposal = pending(proposalId);
        if (reason == null) throw new IllegalArgumentException("拒绝原因必填");
        if (reason == ProposalRejectionReason.OTHER && !StringUtils.hasText(comment)) {
            throw new IllegalArgumentException("拒绝原因为 OTHER 时必须填写评论");
        }
        Instant now = Instant.now();
        proposal.setProposalStatus(ProposalStatus.REJECTED); proposal.setRejectionReason(reason.name());
        proposal.setReviewedBy(operator(operator)); proposal.setReviewComment(comment); proposal.setReviewedAt(now);
        proposal.setClosedAt(now); proposal.setUpdatedAt(now); proposalRepository.save(proposal);
        updateActionStatuses(proposal, ProposalActionStatus.REJECTED, now);
        resolveFeedback(proposal, GovernanceGroupStatus.RESOLVED, FeedbackGovernanceStatus.RESOLVED);
        memoryService.recordDecision(proposal, GovernanceDecision.REJECTED, reason.name(), comment, null);
        audit(proposal, "GOVERNANCE_PROPOSAL_REJECTED", operator, comment, ProposalStatus.PENDING_REVIEW.name(), ProposalStatus.REJECTED.name());
        return proposal;
    }

    @Transactional
    public RuleChangeProposalEntity defer(Long proposalId,
                                          String reason,
                                          Instant reviewAfter,
                                          String operator) {
        RuleChangeProposalEntity proposal = pending(proposalId);
        if (!StringUtils.hasText(reason)) throw new IllegalArgumentException("暂缓原因必填");
        proposal.setProposalStatus(ProposalStatus.DEFERRED); proposal.setDeferReason(reason);
        proposal.setDeferredUntil(reviewAfter); proposal.setReviewedBy(operator(operator)); proposal.setReviewedAt(Instant.now());
        proposal.setUpdatedAt(Instant.now()); proposalRepository.save(proposal);
        updateActionStatuses(proposal, ProposalActionStatus.DEFERRED, Instant.now());
        resolveFeedback(proposal, GovernanceGroupStatus.DEFERRED, FeedbackGovernanceStatus.DEFERRED);
        memoryService.recordDecision(proposal, GovernanceDecision.DEFERRED, reason, null, null);
        audit(proposal, "GOVERNANCE_PROPOSAL_DEFERRED", operator, reason, ProposalStatus.PENDING_REVIEW.name(), ProposalStatus.DEFERRED.name());
        return proposal;
    }

    @Transactional
    public RuleChangeProposalEntity applyDisable(Long proposalId, String operator, String comment) {
        RuleChangeProposalEntity proposal = proposalRepository.findLockedById(proposalId)
                .orElseThrow(() -> new IllegalArgumentException("提案不存在: " + proposalId));
        if (proposal.getProposalType() == ProposalType.COMPOSITE_RULE_CHANGE && proposal.getProposalStatus() == ProposalStatus.APPROVED) {
            return applyComposite(proposal, operator, comment);
        }
        if (proposal.getProposalType() != ProposalType.DISABLE_RULE || proposal.getProposalStatus() != ProposalStatus.APPROVED) {
            throw new IllegalStateException("只有已批准的停用提案可以执行停用");
        }
        ReviewRuleDefinitionEntity definition = proposal.getRuleDefinition();
        String before = String.valueOf(definition.isEnabled());
        definition.setEnabled(false); definition.setUpdatedAt(Instant.now()); definition.setUpdatedBy(operator(operator));
        definitionRepository.save(definition);
        proposal.setProposalStatus(ProposalStatus.APPLIED); proposal.setAppliedAt(Instant.now()); proposal.setUpdatedAt(Instant.now());
        proposalRepository.save(proposal);
        memoryService.recordEffect(proposal, GovernanceDecision.UNKNOWN,
                jsonService.json(java.util.Map.of("status", "APPLIED", "action", "RULE_DISABLED")));
        audit(proposal, "GOVERNANCE_RULE_DISABLED", operator, comment, before, "false");
        return proposal;
    }

    private RuleChangeProposalEntity applyComposite(RuleChangeProposalEntity proposal,
                                                    String operator,
                                                    String comment) {
        List<RuleChangeProposalActionEntity> actions = actionRepository.findByProposal_IdOrderBySequenceNoAsc(proposal.getId());
        if (actions.isEmpty()) throw new IllegalStateException("复合提案缺少子动作");
        List<RuleChangeProposalActionEntity> draftActions = actions.stream()
                .filter(action -> action.getActionStatus() == ProposalActionStatus.DRAFT_CREATED)
                .toList();
        List<RuleChangeProposalActionEntity> disableActions = actions.stream()
                .filter(action -> action.getActionType() == ProposalType.DISABLE_RULE)
                .filter(action -> action.getActionStatus() == ProposalActionStatus.DISABLE_PENDING
                        || action.getActionStatus() == ProposalActionStatus.APPROVED)
                .toList();
        if (draftActions.isEmpty() && disableActions.isEmpty()) {
            throw new IllegalStateException("复合提案没有待应用的规则动作");
        }

        // Validate every replacement before changing any production rule. The transaction then publishes
        // replacements first and disables source rules last, so a failure cannot leave both sides disabled.
        for (RuleChangeProposalActionEntity action : draftActions) validateDraftAction(action);
        Instant now = Instant.now();
        for (RuleChangeProposalActionEntity action : draftActions) {
            publishDraftAction(proposal, action, operator, comment, now);
        }
        for (RuleChangeProposalActionEntity action : disableActions) {
            ReviewRuleDefinitionEntity definition = proposal.getRuleDefinition();
            if (action.getSourceRuleVersion() != null && action.getSourceRuleVersion().getRuleDefinition() != null) {
                definition = action.getSourceRuleVersion().getRuleDefinition();
            }
            String before = String.valueOf(definition.isEnabled());
            definition.setEnabled(false); definition.setUpdatedAt(now); definition.setUpdatedBy(operator(operator));
            definitionRepository.save(definition);
            action.setActionStatus(ProposalActionStatus.APPLIED);
            action.setUpdatedAt(now);
            actionRepository.save(action);
            audit(proposal, "GOVERNANCE_RULE_DISABLED", operator, comment, before, "false");
        }
        List<RuleChangeProposalActionEntity> remaining = actionRepository.findByProposal_IdOrderBySequenceNoAsc(proposal.getId());
        if (remaining.stream().anyMatch(action -> action.getActionStatus() != ProposalActionStatus.APPLIED)) {
            throw new IllegalStateException("复合提案仍有未完成动作，已取消本次应用");
        }
        completeCompositeIfReady(proposal, now);
        proposal.setUpdatedAt(now);
        proposalRepository.save(proposal);
        return proposal;
    }

    private void validateDraftAction(RuleChangeProposalActionEntity action) {
        if (action.getDraftRuleDefinition() == null || action.getDraftRuleVersion() == null) {
            throw new IllegalStateException("规则动作缺少待发布草稿: " + action.getRuleCode());
        }
        if (!"DRAFT".equalsIgnoreCase(action.getDraftRuleVersion().getStatus())) {
            throw new IllegalStateException("规则动作关联版本不是 DRAFT: " + action.getRuleCode());
        }
        RuleCandidate candidate = candidateFrom(action.getAfterRuleSnapshotJson());
        CandidateValidationResult validation = validationService.validate(candidate, candidate.ruleCode(), false);
        if (!validation.valid()) {
            throw new IllegalArgumentException("待发布规则校验失败 " + action.getRuleCode() + ": "
                    + String.join("; ", validation.errors()));
        }
    }

    private void publishDraftAction(RuleChangeProposalEntity proposal,
                                    RuleChangeProposalActionEntity action,
                                    String operator,
                                    String comment,
                                    Instant now) {
        RuleCandidate candidate = candidateFrom(action.getAfterRuleSnapshotJson());
        ReviewRuleDefinitionEntity definition = action.getDraftRuleDefinition();
        ReviewRuleVersionEntity draft = action.getDraftRuleVersion();
        for (ReviewRuleVersionEntity version : versionRepository
                .findByRuleDefinition_IdOrderByVersionNumberDesc(definition.getId())) {
            if (!version.getId().equals(draft.getId()) && version.isActive()) {
                version.setActive(false);
                version.setUpdatedAt(now);
                versionRepository.save(version);
            }
        }
        draft.setStatus("PUBLISHED");
        draft.setActive(true);
        draft.setPublishedAt(now);
        draft.setUpdatedAt(now);
        versionRepository.save(draft);

        String before = jsonService.json(java.util.Map.of(
                "enabled", definition.isEnabled(),
                "activeVersionId", definition.getActiveVersionId() == null ? "" : definition.getActiveVersionId()));
        definition.setActiveVersionId(draft.getId());
        definition.setVersionCode(displayVersionCode(draft.getVersionCode()));
        definition.setRuleType(draft.getExecutorType());
        definition.setRuleCategory(categoryFor(draft.getExecutorType()));
        definition.setEnabled(Boolean.TRUE.equals(candidate.enabled()));
        definition.setUpdatedAt(now);
        definition.setUpdatedBy(operator(operator));
        definitionRepository.save(definition);

        action.setActionStatus(ProposalActionStatus.APPLIED);
        action.setUpdatedAt(now);
        actionRepository.save(action);
        audit(proposal, "GOVERNANCE_DRAFT_PUBLISHED", operator,
                StringUtils.hasText(comment) ? comment : "复合提案二次确认发布规则",
                before, jsonService.json(java.util.Map.of(
                        "enabled", definition.isEnabled(), "activeVersionId", draft.getId())));
    }

    @Transactional
    public void markAppliedForRuleVersion(Long ruleVersionId, String operator) {
        actionRepository.findByDraftRuleVersion_Id(ruleVersionId).ifPresent(action -> {
            RuleChangeProposalEntity proposal = action.getProposal();
            if (proposal.getProposalType() != ProposalType.COMPOSITE_RULE_CHANGE
                    || (proposal.getProposalStatus() != ProposalStatus.APPROVED
                    && proposal.getProposalStatus() != ProposalStatus.APPROVED_WITH_MODIFICATION)) return;
            Instant now = Instant.now();
            RuleCandidate candidate = candidateFrom(action.getAfterRuleSnapshotJson());
            ReviewRuleDefinitionEntity definition = action.getDraftRuleDefinition();
            if (definition != null) {
                definition.setEnabled(Boolean.TRUE.equals(candidate.enabled()));
                definition.setUpdatedAt(now);
                definition.setUpdatedBy(operator(operator));
                definitionRepository.save(definition);
            }
            action.setActionStatus(ProposalActionStatus.APPLIED);
            action.setUpdatedAt(now);
            actionRepository.save(action);
            completeCompositeIfReady(proposal, now);
            proposalRepository.save(proposal);
            audit(proposal, "GOVERNANCE_DRAFT_PUBLISHED", operator, "治理子动作草稿已由人工发布",
                    "DRAFT", "PUBLISHED");
        });
        proposalRepository.findByDraftRuleVersion_Id(ruleVersionId).ifPresent(proposal -> {
            if (proposal.getProposalStatus() != ProposalStatus.APPROVED
                    && proposal.getProposalStatus() != ProposalStatus.APPROVED_WITH_MODIFICATION) return;
            if (proposal.getProposalType() == ProposalType.COMPOSITE_RULE_CHANGE) return;
            proposal.setProposalStatus(ProposalStatus.APPLIED); proposal.setAppliedAt(Instant.now()); proposal.setUpdatedAt(Instant.now());
            proposalRepository.save(proposal);
            memoryService.recordEffect(proposal, GovernanceDecision.UNKNOWN,
                    jsonService.json(java.util.Map.of("status", "APPLIED", "ruleVersionId", ruleVersionId)));
            audit(proposal, "GOVERNANCE_DRAFT_PUBLISHED", operator, "治理草稿已由人工发布",
                    "DRAFT", "PUBLISHED");
        });
    }

    private RuleChangeProposalEntity accept(RuleChangeProposalEntity proposal,
                                            RuleCandidate candidate,
                                            boolean modified,
                                            String operator,
                                            String comment,
                                            String validationJson,
                                            String backtestJson) {
        Instant now = Instant.now();
        ReviewRuleVersionEntity draft = null;
        if (proposal.getProposalType() == ProposalType.UPDATE_RULE || proposal.getProposalType() == ProposalType.CREATE_EXCEPTION) {
            draft = createDraft(proposal.getRuleDefinition(), candidate, proposal, now);
            proposal.setDraftRuleDefinition(proposal.getRuleDefinition()); proposal.setDraftRuleVersion(draft);
        } else if (proposal.getProposalType() == ProposalType.CREATE_RULE) {
            ReviewRuleDefinitionEntity definition = createDefinition(candidate, now, operator);
            draft = createDraft(definition, candidate, proposal, now);
            proposal.setDraftRuleDefinition(definition); proposal.setDraftRuleVersion(draft);
        }
        if (modified) {
            proposal.setFinalRuleSnapshotJson(jsonService.json(mapper.valueToTree(candidate)));
            proposal.setValidationResultJson(validationJson); proposal.setBacktestResultJson(backtestJson);
        } else {
            proposal.setFinalRuleSnapshotJson(proposal.getAfterRuleSnapshotJson());
        }
        proposal.setProposalStatus(modified ? ProposalStatus.APPROVED_WITH_MODIFICATION : ProposalStatus.APPROVED);
        proposal.setReviewedAt(now); proposal.setReviewedBy(operator(operator)); proposal.setReviewComment(comment);
        proposal.setUpdatedAt(now); proposalRepository.save(proposal);
        resolveFeedback(proposal, GovernanceGroupStatus.RESOLVED, FeedbackGovernanceStatus.RESOLVED);
        memoryService.recordDecision(proposal, modified ? GovernanceDecision.MODIFIED_AND_ACCEPTED : GovernanceDecision.ACCEPTED,
                null, comment, proposal.getFinalRuleSnapshotJson());
        audit(proposal, modified ? "GOVERNANCE_PROPOSAL_MODIFIED_APPROVED" : "GOVERNANCE_PROPOSAL_APPROVED",
                operator, comment, ProposalStatus.PENDING_REVIEW.name(), proposal.getProposalStatus().name());
        return proposal;
    }

    private RuleChangeProposalEntity acceptComposite(RuleChangeProposalEntity proposal,
                                                     String operator,
                                                     String comment) {
        Instant now = Instant.now();
        List<RuleChangeProposalActionEntity> actions = actionRepository.findByProposal_IdOrderBySequenceNoAsc(proposal.getId());
        if (actions.isEmpty()) throw new IllegalStateException("复合提案缺少子动作");
        ReviewRuleVersionEntity firstDraft = null;
        ReviewRuleDefinitionEntity firstDraftDefinition = null;
        for (RuleChangeProposalActionEntity action : actions) {
            RuleCandidate candidate = candidateFrom(action.getAfterRuleSnapshotJson());
            if (action.getActionType() == ProposalType.CREATE_RULE) {
                ReviewRuleDefinitionEntity definition = createDefinition(candidate, now, operator);
                ReviewRuleVersionEntity draft = createDraft(definition, candidate, proposal, now);
                action.setDraftRuleDefinition(definition);
                action.setDraftRuleVersion(draft);
                action.setActionStatus(ProposalActionStatus.DRAFT_CREATED);
                if (firstDraft == null) {
                    firstDraft = draft;
                    firstDraftDefinition = definition;
                }
            } else if (action.getActionType() == ProposalType.UPDATE_RULE
                    || action.getActionType() == ProposalType.CREATE_EXCEPTION) {
                ReviewRuleDefinitionEntity definition = action.getSourceRuleVersion() == null
                        ? proposal.getRuleDefinition() : action.getSourceRuleVersion().getRuleDefinition();
                ReviewRuleVersionEntity draft = createDraft(definition, candidate, proposal, now);
                action.setDraftRuleDefinition(definition);
                action.setDraftRuleVersion(draft);
                action.setActionStatus(ProposalActionStatus.DRAFT_CREATED);
                if (firstDraft == null) {
                    firstDraft = draft;
                    firstDraftDefinition = definition;
                }
            } else if (action.getActionType() == ProposalType.DISABLE_RULE) {
                action.setActionStatus(ProposalActionStatus.DISABLE_PENDING);
            } else {
                action.setActionStatus(ProposalActionStatus.APPROVED);
            }
            action.setUpdatedAt(now);
            actionRepository.save(action);
        }
        if (firstDraft != null) {
            proposal.setDraftRuleDefinition(firstDraftDefinition);
            proposal.setDraftRuleVersion(firstDraft);
        }
        proposal.setFinalRuleSnapshotJson(proposal.getAfterRuleSnapshotJson());
        proposal.setProposalStatus(ProposalStatus.APPROVED);
        proposal.setReviewedAt(now);
        proposal.setReviewedBy(operator(operator));
        proposal.setReviewComment(comment);
        proposal.setUpdatedAt(now);
        proposalRepository.save(proposal);
        resolveFeedback(proposal, GovernanceGroupStatus.RESOLVED, FeedbackGovernanceStatus.RESOLVED);
        memoryService.recordDecision(proposal, GovernanceDecision.ACCEPTED, null, comment, proposal.getFinalRuleSnapshotJson());
        audit(proposal, "GOVERNANCE_COMPOSITE_PROPOSAL_APPROVED", operator, comment,
                ProposalStatus.PENDING_REVIEW.name(), ProposalStatus.APPROVED.name());
        return proposal;
    }

    private ReviewRuleDefinitionEntity createDefinition(RuleCandidate candidate, Instant now, String operator) {
        if (definitionRepository.findByRuleCode(candidate.ruleCode()).isPresent()) throw new IllegalStateException("规则代码已存在");
        ReviewRuleDefinitionEntity definition = candidate.definition();
        definition.setEnabled(false); definition.setCreatedAt(now); definition.setUpdatedAt(now);
        definition.setCreatedBy(operator(operator)); definition.setUpdatedBy(operator(operator));
        definition.setSeverity(candidate.action().path("severity").asText("MEDIUM"));
        definition.setConfidence(candidate.action().path("confidence").asDouble(0.7));
        return definitionRepository.save(definition);
    }

    private ReviewRuleVersionEntity createDraft(ReviewRuleDefinitionEntity definition,
                                                RuleCandidate candidate,
                                                RuleChangeProposalEntity proposal,
                                                Instant now) {
        ReviewRuleVersionEntity draft = candidate.version(mapper);
        int next = versionRepository.findFirstByRuleDefinition_IdOrderByVersionNumberDesc(definition.getId())
                .map(v -> v.getVersionNumber() == null ? 1 : v.getVersionNumber() + 1).orElse(1);
        draft.setRuleDefinition(definition); draft.setVersionNumber(next);
        draft.setVersionCode(storageVersionCode(definition.getRuleCode(), next));
        draft.setDescription("由治理提案 " + proposal.getProposalNo() + " 创建");
        draft.setChangeSummary(proposal.getChangeReason()); draft.setStatus("DRAFT"); draft.setActive(false);
        draft.setSourceProposalId(proposal.getId()); draft.setCreatedAt(now); draft.setUpdatedAt(now);
        return versionRepository.save(draft);
    }

    private void resolveFeedback(RuleChangeProposalEntity proposal,
                                 GovernanceGroupStatus groupStatus,
                                 FeedbackGovernanceStatus feedbackStatus) {
        RuleFeedbackGovernanceGroupEntity group = proposal.getGovernanceGroup();
        group.setStatus(groupStatus); group.setUpdatedAt(Instant.now()); groupRepository.save(group);
        for (RuleChangeProposalFeedbackEntity link : proposalFeedbackRepository.findByProposal_IdOrderByFeedback_CreatedAtDesc(proposal.getId())) {
            ReviewRuleFeedbackEntity feedback = link.getFeedback();
            feedback.setProcessStatus(feedbackStatus.name()); feedback.setProcessedAt(Instant.now()); feedbackRepository.save(feedback);
        }
    }

    private RuleChangeProposalEntity pending(Long proposalId) {
        RuleChangeProposalEntity proposal = proposalRepository.findLockedById(proposalId)
                .orElseThrow(() -> new IllegalArgumentException("提案不存在: " + proposalId));
        if (proposal.getProposalStatus() != ProposalStatus.PENDING_REVIEW) throw new IllegalStateException("提案已被其他用户处理");
        return proposal;
    }

    private void updateActionStatuses(RuleChangeProposalEntity proposal,
                                      ProposalActionStatus status,
                                      Instant now) {
        for (RuleChangeProposalActionEntity action : actionRepository.findByProposal_IdOrderBySequenceNoAsc(proposal.getId())) {
            action.setActionStatus(status);
            action.setUpdatedAt(now);
            actionRepository.save(action);
        }
    }

    private void completeCompositeIfReady(RuleChangeProposalEntity proposal, Instant now) {
        if (proposal.getProposalType() != ProposalType.COMPOSITE_RULE_CHANGE) return;
        List<RuleChangeProposalActionEntity> actions = actionRepository.findByProposal_IdOrderBySequenceNoAsc(proposal.getId());
        if (actions.isEmpty() || actions.stream().anyMatch(action -> action.getActionStatus() != ProposalActionStatus.APPLIED)) {
            return;
        }
        if (proposal.getProposalStatus() == ProposalStatus.APPLIED) return;
        proposal.setProposalStatus(ProposalStatus.APPLIED);
        proposal.setAppliedAt(now);
        proposal.setUpdatedAt(now);
        memoryService.recordEffect(proposal, GovernanceDecision.UNKNOWN,
                jsonService.json(java.util.Map.of("status", "APPLIED", "action", "COMPOSITE_RULE_CHANGE")));
        audit(proposal, "GOVERNANCE_COMPOSITE_APPLIED", "SYSTEM", "复合提案所有子动作已完成",
                "APPROVED", "APPLIED");
    }

    private RuleCandidate candidateFrom(String json) {
        if (!StringUtils.hasText(json)) return null;
        return RuleCandidate.from(jsonService.tree(json), mapper);
    }
    private boolean requiresCandidate(ProposalType type) { return List.of(ProposalType.UPDATE_RULE, ProposalType.DISABLE_RULE, ProposalType.CREATE_RULE, ProposalType.CREATE_EXCEPTION).contains(type); }
    private record ModificationContext(ProposalStatus status, ProposalType type, Long groupId, String ruleCode) {}
    private String operator(String value) { return StringUtils.hasText(value) ? value.strip() : "demo-user"; }
    private String storageVersionCode(String ruleCode, Integer versionNumber) {
        String suffix = ":v" + (versionNumber == null || versionNumber < 1 ? 1 : versionNumber);
        String base = StringUtils.hasText(ruleCode) ? ruleCode.strip() : "RULE";
        if (base.length() + suffix.length() <= 64) return base + suffix;
        String hash = "~" + Integer.toUnsignedString(base.hashCode(), 36);
        int prefixLength = Math.max(1, 64 - suffix.length() - hash.length());
        return base.substring(0, prefixLength) + hash + suffix;
    }
    private String displayVersionCode(String versionCode) {
        if (!StringUtils.hasText(versionCode)) return versionCode;
        String value = versionCode.strip();
        int index = value.lastIndexOf(':');
        return index >= 0 && index < value.length() - 1 ? value.substring(index + 1) : value;
    }
    private String categoryFor(String executorType) {
        RuleExecutorType type;
        try {
            type = RuleExecutorType.valueOf(StringUtils.hasText(executorType)
                    ? executorType.strip().toUpperCase(java.util.Locale.ROOT) : RuleExecutorType.JAVA_PLUGIN.name());
        } catch (Exception ignored) {
            type = RuleExecutorType.JAVA_PLUGIN;
        }
        return switch (type) {
            case HYBRID -> "HYBRID";
            case LLM_POLICY -> "LLM_POLICY";
            case JAVA_PLUGIN -> "JAVA_PLUGIN";
            default -> "HARD_CONFIG";
        };
    }
    private void audit(RuleChangeProposalEntity proposal, String operation, String operator, String detail, String before, String after) {
        String ids = proposalFeedbackRepository.findByProposal_IdOrderByFeedback_CreatedAtDesc(proposal.getId()).stream()
                .map(link -> String.valueOf(link.getFeedback().getId())).reduce((a, b) -> a + "," + b).orElse("");
        auditLogService.recordGovernance(proposal.getGovernanceRun().getId(), proposal.getGovernanceGroup().getId(),
                proposal.getId(), proposal.getRuleCode(), ids, operation, operator(operator), detail, before, after);
    }
}
