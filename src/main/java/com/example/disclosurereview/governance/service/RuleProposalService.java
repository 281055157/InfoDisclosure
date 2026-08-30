package com.example.disclosurereview.governance.service;

import com.example.disclosurereview.config.FeedbackGovernanceProperties;
import com.example.disclosurereview.governance.domain.*;
import com.example.disclosurereview.governance.persistence.entity.*;
import com.example.disclosurereview.governance.persistence.repository.*;
import com.example.disclosurereview.persistence.entity.ReviewRuleFeedbackEntity;
import com.example.disclosurereview.persistence.entity.ModelCallRecordEntity;
import com.example.disclosurereview.persistence.repository.LlmCallAttemptJpaRepository;
import com.example.disclosurereview.persistence.repository.ModelCallRecordJpaRepository;
import com.example.disclosurereview.service.AuditLogService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class RuleProposalService {
    private static final List<ProposalStatus> ACTIVE = List.of(
            ProposalStatus.DRAFT, ProposalStatus.PENDING_REVIEW,
            ProposalStatus.APPROVED, ProposalStatus.APPROVED_WITH_MODIFICATION);
    private final RuleFeedbackGovernanceGroupJpaRepository groupRepository;
    private final RuleChangeProposalJpaRepository proposalRepository;
    private final RuleChangeProposalFeedbackJpaRepository proposalFeedbackRepository;
    private final RuleChangeProposalActionJpaRepository actionRepository;
    private final RuleGovernanceToolCallJpaRepository toolCallRepository;
    private final RuleGovernanceRunJpaRepository runRepository;
    private final GovernanceActionPolicy actionPolicy;
    private final RuleCandidateValidationService validationService;
    private final RuleSnapshotService snapshotService;
    private final FeedbackGovernanceGroupService groupService;
    private final GovernanceMemoryService memoryService;
    private final GovernanceJsonService jsonService;
    private final FeedbackGovernanceProperties properties;
    private final AuditLogService auditLogService;
    private final ObjectMapper mapper;
    private final LlmCallAttemptJpaRepository llmAttemptRepository;
    private final ModelCallRecordJpaRepository modelCallRepository;

    public RuleProposalService(RuleFeedbackGovernanceGroupJpaRepository groupRepository,
                               RuleChangeProposalJpaRepository proposalRepository,
                               RuleChangeProposalFeedbackJpaRepository proposalFeedbackRepository,
                               RuleChangeProposalActionJpaRepository actionRepository,
                               RuleGovernanceToolCallJpaRepository toolCallRepository,
                               RuleGovernanceRunJpaRepository runRepository,
                               GovernanceActionPolicy actionPolicy,
                               RuleCandidateValidationService validationService,
                               RuleSnapshotService snapshotService,
                               FeedbackGovernanceGroupService groupService,
                               GovernanceMemoryService memoryService,
                               GovernanceJsonService jsonService,
                               FeedbackGovernanceProperties properties,
                               AuditLogService auditLogService,
                               ObjectMapper mapper,
                               LlmCallAttemptJpaRepository llmAttemptRepository,
                               ModelCallRecordJpaRepository modelCallRepository) {
        this.groupRepository = groupRepository;
        this.proposalRepository = proposalRepository;
        this.proposalFeedbackRepository = proposalFeedbackRepository;
        this.actionRepository = actionRepository;
        this.toolCallRepository = toolCallRepository;
        this.runRepository = runRepository;
        this.actionPolicy = actionPolicy;
        this.validationService = validationService;
        this.snapshotService = snapshotService;
        this.groupService = groupService;
        this.memoryService = memoryService;
        this.jsonService = jsonService;
        this.properties = properties;
        this.auditLogService = auditLogService;
        this.mapper = mapper;
        this.llmAttemptRepository = llmAttemptRepository;
        this.modelCallRepository = modelCallRepository;
    }

    @Transactional
    public RuleChangeProposalEntity create(Long runId,
                                           Long groupId,
                                           ProposalCreateCommand command,
                                           String rawAgentResponse) {
        RuleFeedbackGovernanceGroupEntity group = groupRepository.findLockedById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("治理分组不存在: " + groupId));
        if (!group.getGovernanceRun().getId().equals(runId)) throw new IllegalArgumentException("治理运行与分组不匹配");
        if (proposalRepository.existsByGovernanceGroup_IdAndProposalStatusIn(groupId, ACTIVE)) {
            throw new IllegalStateException("该治理分组已有未结束提案");
        }
        requireProposalMatchesIntent(group, command.proposalType());
        actionPolicy.requireAllowed(command.rootCauseType(), command.proposalType());
        requireText(command.problemSummary(), "problemSummary");
        requireText(command.rootCauseAnalysis(), "rootCauseAnalysis");
        double confidence = command.agentConfidence() == null ? 0 : command.agentConfidence();
        if (requiresRuleCandidate(command.proposalType())
                && confidence < properties.getAgent().getMinimumConfidenceForRuleChange()) {
            throw new IllegalArgumentException("规则变更提案置信度低于阈值");
        }
        if (command.proposalType() == ProposalType.COMPOSITE_RULE_CHANGE) {
            if (confidence < properties.getAgent().getMinimumConfidenceForRuleChange()) {
                throw new IllegalArgumentException("规则变更提案置信度低于阈值");
            }
            requireText(command.changeReason(), "changeReason");
            requireText(command.expectedEffect(), "expectedEffect");
            requireText(command.riskDescription(), "riskDescription");
            return createComposite(runId, groupId, group, command, confidence, rawAgentResponse);
        }

        RuleCandidate candidate = null;
        String validationJson = null;
        String backtestJson = null;
        String affectedScopeJson = null;
        String candidateHash = null;
        boolean forceHumanFollowUp = false;
        if (requiresRuleCandidate(command.proposalType())) {
            if (command.candidateRule() == null || !command.candidateRule().isObject()) {
                throw new IllegalArgumentException("该提案类型必须包含 candidateRule");
            }
            candidate = RuleCandidate.from(command.candidateRule(), mapper);
            boolean creating = command.proposalType() == ProposalType.CREATE_RULE;
            if (command.proposalType() == ProposalType.DISABLE_RULE && !Boolean.FALSE.equals(candidate.enabled())) {
                throw new IllegalArgumentException("停用提案的 candidateRule.enabled 必须为 false");
            }
            if (!creating && !group.getRuleCode().equals(candidate.ruleCode())) {
                throw new IllegalArgumentException("候选规则必须沿用源规则代码");
            }
            CandidateValidationResult currentValidation = validationService.validate(candidate, group.getRuleCode(), creating);
            if (!currentValidation.valid()) throw new IllegalArgumentException("候选规则校验失败: " + String.join("; ", currentValidation.errors()));
            candidateHash = currentValidation.candidateHash();
            RuleGovernanceToolCallEntity validationCall = requireArtifact(runId, groupId,
                    "validateRuleConfig", candidateHash);
            RuleGovernanceToolCallEntity backtestCall = requireArtifact(runId, groupId,
                    "runRuleBacktest", candidateHash);
            requireArtifact(runId, groupId, "checkRuleConflict", candidateHash);
            RuleGovernanceToolCallEntity affectedScopeCall = requireArtifact(runId, groupId,
                    "estimateAffectedDocuments", candidateHash);
            if (!creating) requireArtifact(runId, groupId, "compareRuleVersions", candidateHash);
            if (candidate.executorType() == com.example.disclosurereview.rule.domain.RuleExecutorType.REGEX) {
                requireArtifact(runId, groupId, "compileRegex", candidateHash);
            }
            validationJson = validationCall.getOutputJson();
            backtestJson = backtestCall.getOutputJson();
            forceHumanFollowUp = validateBacktestArtifact(backtestJson, candidate);
            affectedScopeJson = affectedScopeCall.getOutputJson();
        } else if (command.proposalType() == ProposalType.OPTIMIZATION_ADVICE) {
            requireText(command.optimizationCategory(), "optimizationCategory");
            requireText(command.optimizationAdvice(), "optimizationAdvice");
        }

        Instant now = Instant.now();
        RuleChangeProposalEntity proposal = new RuleChangeProposalEntity();
        proposal.setProposalNo(proposalNo(now));
        proposal.setProposalType(command.proposalType());
        proposal.setProposalStatus(ProposalStatus.PENDING_REVIEW);
        proposal.setGovernanceGroup(group);
        proposal.setGovernanceRun(group.getGovernanceRun());
        proposal.setRuleDefinition(group.getRuleDefinition());
        proposal.setRuleCode(command.proposalType() == ProposalType.CREATE_RULE && candidate != null
                ? candidate.ruleCode() : group.getRuleCode());
        proposal.setSourceRuleVersionEntity(group.getRuleVersionEntity());
        proposal.setSourceRuleVersion(group.getRuleVersion());
        proposal.setRootCauseType(command.rootCauseType());
        proposal.setAgentConfidence(confidence);
        proposal.setProblemSummary(command.problemSummary());
        proposal.setRootCauseAnalysis(command.rootCauseAnalysis());
        proposal.setChangeReason(command.changeReason());
        proposal.setExpectedEffect(command.expectedEffect());
        proposal.setRiskDescription(command.riskDescription());
        proposal.setBeforeRuleSnapshotJson(jsonService.json(snapshotService.snapshot(group.getRuleDefinition(), group.getRuleVersionEntity())));
        proposal.setAfterRuleSnapshotJson(candidate == null ? null : jsonService.json(command.candidateRule()));
        proposal.setChangeContentJson(candidate == null ? "{}" : jsonService.json(command.candidateRule()));
        proposal.setValidationResultJson(validationJson);
        proposal.setBacktestResultJson(backtestJson);
        proposal.setAffectedScopeJson(affectedScopeJson);
        proposal.setOptimizationCategory(command.optimizationCategory());
        proposal.setOptimizationAdvice(command.optimizationAdvice());
        proposal.setResponsibleModule(command.responsibleModule());
        proposal.setProposalPriority(StringUtils.hasText(command.priority()) ? command.priority() : "MEDIUM");
        proposal.setHumanFollowUpRequired(Boolean.TRUE.equals(command.humanFollowUpRequired())
                || forceHumanFollowUp || group.isRuleGap());
        proposal.setAgentPromptVersion(properties.getAgent().getPromptVersion());
        proposal.setAgentResponseJson(rawAgentResponse);
        proposal.setSubmittedAt(now);
        proposal.setCreatedBy("GOVERNANCE_AGENT");
        proposal.setCreatedAt(now);
        proposal.setUpdatedAt(now);
        proposal = proposalRepository.save(proposal);

        List<ReviewRuleFeedbackEntity> feedbacks = groupService.feedbacks(groupId);
        for (ReviewRuleFeedbackEntity feedback : feedbacks) {
            RuleChangeProposalFeedbackEntity link = new RuleChangeProposalFeedbackEntity();
            link.setProposal(proposal);
            link.setFeedback(feedback);
            link.setCreatedAt(now);
            proposalFeedbackRepository.save(link);
            feedback.setProcessStatus(FeedbackGovernanceStatus.PROPOSAL_CREATED.name());
        }
        group.setStatus(GovernanceGroupStatus.PROPOSAL_CREATED);
        group.setUpdatedAt(now);
        groupRepository.save(group);
        RuleGovernanceRunEntity run = group.getGovernanceRun();
        run.setCreatedProposalCount(run.getCreatedProposalCount() + 1);
        run.setUpdatedAt(now);
        runRepository.save(run);
        memoryService.recordCase(proposal);
        auditLogService.recordGovernance(runId, groupId, proposal.getId(), proposal.getRuleCode(),
                feedbacks.stream().map(f -> String.valueOf(f.getId())).reduce((a, b) -> a + "," + b).orElse(""),
                "GOVERNANCE_PROPOSAL_CREATED", "GOVERNANCE_AGENT", command.problemSummary(),
                proposal.getBeforeRuleSnapshotJson(), proposal.getAfterRuleSnapshotJson());
        return proposal;
    }

    private RuleChangeProposalEntity createComposite(Long runId,
                                                     Long groupId,
                                                     RuleFeedbackGovernanceGroupEntity group,
                                                     ProposalCreateCommand command,
                                                     double confidence,
                                                     String rawAgentResponse) {
        List<ActionPlan> actions = prepareCompositeActions(runId, group, command);
        Instant now = Instant.now();
        RuleChangeProposalEntity proposal = new RuleChangeProposalEntity();
        proposal.setProposalNo(proposalNo(now));
        proposal.setProposalType(ProposalType.COMPOSITE_RULE_CHANGE);
        proposal.setProposalStatus(ProposalStatus.PENDING_REVIEW);
        proposal.setGovernanceGroup(group);
        proposal.setGovernanceRun(group.getGovernanceRun());
        proposal.setRuleDefinition(group.getRuleDefinition());
        proposal.setRuleCode(group.getRuleCode());
        proposal.setSourceRuleVersionEntity(group.getRuleVersionEntity());
        proposal.setSourceRuleVersion(group.getRuleVersion());
        proposal.setRootCauseType(command.rootCauseType());
        proposal.setAgentConfidence(confidence);
        proposal.setProblemSummary(command.problemSummary());
        proposal.setRootCauseAnalysis(command.rootCauseAnalysis());
        proposal.setChangeReason(command.changeReason());
        proposal.setExpectedEffect(command.expectedEffect());
        proposal.setRiskDescription(command.riskDescription());
        proposal.setBeforeRuleSnapshotJson(jsonService.json(snapshotService.snapshot(group.getRuleDefinition(), group.getRuleVersionEntity())));
        proposal.setAfterRuleSnapshotJson(jsonService.json(compositeSnapshot(actions)));
        proposal.setChangeContentJson(jsonService.json(compositeSnapshot(actions)));
        proposal.setValidationResultJson(jsonService.json(aggregate(actions, "validation")));
        proposal.setBacktestResultJson(jsonService.json(aggregate(actions, "backtest")));
        proposal.setAffectedScopeJson(jsonService.json(aggregate(actions, "affectedScope")));
        proposal.setResponsibleModule(command.responsibleModule());
        proposal.setProposalPriority(StringUtils.hasText(command.priority()) ? command.priority() : "MEDIUM");
        proposal.setHumanFollowUpRequired(Boolean.TRUE.equals(command.humanFollowUpRequired())
                || actions.stream().anyMatch(action -> partialBacktest(action.backtestJson())));
        proposal.setAgentPromptVersion(properties.getAgent().getPromptVersion());
        proposal.setAgentResponseJson(rawAgentResponse);
        proposal.setSubmittedAt(now);
        proposal.setCreatedBy("GOVERNANCE_AGENT");
        proposal.setCreatedAt(now);
        proposal.setUpdatedAt(now);
        proposal = proposalRepository.save(proposal);

        for (ActionPlan action : actions) {
            RuleChangeProposalActionEntity row = new RuleChangeProposalActionEntity();
            row.setProposal(proposal);
            row.setSequenceNo(action.sequenceNo());
            row.setActionType(action.actionType());
            row.setActionStatus(ProposalActionStatus.PENDING_REVIEW);
            row.setRuleCode(action.candidate().ruleCode());
            row.setSourceRuleVersion(action.sourceRuleVersion());
            row.setCandidateHash(action.candidateHash());
            row.setBeforeRuleSnapshotJson(action.beforeJson());
            row.setAfterRuleSnapshotJson(action.afterJson());
            row.setCompareResultJson(action.compareJson());
            row.setValidationResultJson(action.validationJson());
            row.setBacktestResultJson(action.backtestJson());
            row.setAffectedScopeJson(action.affectedScopeJson());
            row.setCreatedAt(now);
            row.setUpdatedAt(now);
            actionRepository.save(row);
        }

        List<ReviewRuleFeedbackEntity> feedbacks = groupService.feedbacks(groupId);
        for (ReviewRuleFeedbackEntity feedback : feedbacks) {
            RuleChangeProposalFeedbackEntity link = new RuleChangeProposalFeedbackEntity();
            link.setProposal(proposal);
            link.setFeedback(feedback);
            link.setCreatedAt(now);
            proposalFeedbackRepository.save(link);
            feedback.setProcessStatus(FeedbackGovernanceStatus.PROPOSAL_CREATED.name());
        }
        group.setStatus(GovernanceGroupStatus.PROPOSAL_CREATED);
        group.setUpdatedAt(now);
        groupRepository.save(group);
        RuleGovernanceRunEntity run = group.getGovernanceRun();
        run.setCreatedProposalCount(run.getCreatedProposalCount() + 1);
        run.setUpdatedAt(now);
        runRepository.save(run);
        memoryService.recordCase(proposal);
        auditLogService.recordGovernance(runId, groupId, proposal.getId(), proposal.getRuleCode(),
                feedbacks.stream().map(f -> String.valueOf(f.getId())).reduce((a, b) -> a + "," + b).orElse(""),
                "GOVERNANCE_PROPOSAL_CREATED", "GOVERNANCE_AGENT", command.problemSummary(),
                proposal.getBeforeRuleSnapshotJson(), proposal.getAfterRuleSnapshotJson());
        return proposal;
    }

    private List<ActionPlan> prepareCompositeActions(Long runId,
                                                     RuleFeedbackGovernanceGroupEntity group,
                                                     ProposalCreateCommand command) {
        if (command.actions() == null || command.actions().isEmpty()) {
            throw new IllegalArgumentException("复合提案必须包含 actions");
        }
        List<PreparedCompositeAction> prepared = new ArrayList<>();
        List<MissingCompositeArtifacts> missing = new ArrayList<>();
        int sequence = 1;
        for (ProposalActionCommand action : command.actions()) {
            int sequenceNo = sequence++;
            ProposalType actionType = action == null ? null : action.actionType();
            if (!List.of(ProposalType.UPDATE_RULE, ProposalType.DISABLE_RULE, ProposalType.CREATE_RULE,
                    ProposalType.CREATE_EXCEPTION).contains(actionType)) {
                throw new IllegalArgumentException("复合提案不支持的子动作类型: " + actionType);
            }
            if (action.candidateRule() == null || !action.candidateRule().isObject()) {
                throw new IllegalArgumentException("复合提案子动作必须包含 candidateRule");
            }
            RuleCandidate candidate = RuleCandidate.from(action.candidateRule(), mapper);
            boolean creating = actionType == ProposalType.CREATE_RULE;
            if (group.isRuleGap() && !creating) {
                throw new IllegalArgumentException("RULE_GAP 复合提案只允许 CREATE_RULE 子动作");
            }
            if (StringUtils.hasText(action.ruleCode()) && !action.ruleCode().equals(candidate.ruleCode())) {
                throw new IllegalArgumentException("子动作 ruleCode 必须与 candidateRule.ruleCode 一致");
            }
            if (!creating && !Objects.equals(group.getRuleCode(), candidate.ruleCode())) {
                throw new IllegalArgumentException("非创建子动作必须沿用源规则代码");
            }
            if (!creating && action.sourceRuleVersionId() != null
                    && (group.getRuleVersionEntity() == null
                    || !action.sourceRuleVersionId().equals(group.getRuleVersionEntity().getId()))) {
                throw new IllegalArgumentException("非创建子动作 sourceRuleVersionId 必须等于当前治理分组源版本");
            }
            if (actionType == ProposalType.DISABLE_RULE && !Boolean.FALSE.equals(candidate.enabled())) {
                throw new IllegalArgumentException("停用子动作的 candidateRule.enabled 必须为 false");
            }
            CandidateValidationResult validation = validationService.validate(candidate, group.getRuleCode(), creating);
            if (!validation.valid()) {
                throw new IllegalArgumentException("候选规则校验失败: " + String.join("; ", validation.errors()));
            }
            String hash = validation.candidateHash();
            Map<String, RuleGovernanceToolCallEntity> artifacts = new LinkedHashMap<>();
            List<String> missingTools = new ArrayList<>();
            for (String toolName : requiredArtifacts(creating, candidate)) {
                Optional<RuleGovernanceToolCallEntity> artifact = findArtifact(runId, group.getId(), toolName, hash);
                if (artifact.isPresent()) artifacts.put(toolName, artifact.get());
                else missingTools.add(toolName);
            }
            prepared.add(new PreparedCompositeAction(sequenceNo, actionType, candidate, action.candidateRule(), creating,
                    hash, artifacts));
            if (!missingTools.isEmpty()) {
                missing.add(new MissingCompositeArtifacts(sequenceNo, actionType, candidate.ruleCode(), hash,
                        List.copyOf(missingTools)));
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(missingCompositeArtifactsMessage(missing));
        }

        List<ActionPlan> result = new ArrayList<>();
        for (PreparedCompositeAction action : prepared) {
            Map<String, RuleGovernanceToolCallEntity> artifacts = action.artifacts();
            validateBacktestArtifact(artifacts.get("runRuleBacktest").getOutputJson(), action.candidate());
            RuleGovernanceToolCallEntity compareCall = artifacts.get("compareRuleVersions");
            String beforeJson = action.creating() ? "{}"
                    : jsonService.json(snapshotService.snapshot(group.getRuleDefinition(), group.getRuleVersionEntity()));
            result.add(new ActionPlan(action.sequenceNo(), action.actionType(), action.candidate(),
                    action.creating() ? null : group.getRuleVersionEntity(), action.candidateHash(), beforeJson,
                    jsonService.json(action.candidateRule()), compareCall == null ? null : compareCall.getOutputJson(),
                    artifacts.get("validateRuleConfig").getOutputJson(),
                    artifacts.get("runRuleBacktest").getOutputJson(),
                    artifacts.get("estimateAffectedDocuments").getOutputJson()));
        }
        return List.copyOf(result);
    }

    private List<String> requiredArtifacts(boolean creating, RuleCandidate candidate) {
        List<String> required = new ArrayList<>(List.of(
                "validateRuleConfig", "runRuleBacktest", "checkRuleConflict", "estimateAffectedDocuments"));
        if (!creating) required.add("compareRuleVersions");
        if (candidate.executorType() == com.example.disclosurereview.rule.domain.RuleExecutorType.REGEX) {
            required.add("compileRegex");
        }
        return List.copyOf(required);
    }

    private String missingCompositeArtifactsMessage(List<MissingCompositeArtifacts> missing) {
        var root = mapper.createObjectNode();
        root.put("errorCode", "MISSING_COMPOSITE_ARTIFACTS");
        root.put("maxToolsPerRound", properties.getAgent().getMaxToolsPerRound());
        root.put("repairInstruction", "保持原 actions 和 candidateRule 不变；每个动作先完成 validateRuleConfig，"
                + "再按 missingTools 补齐同 candidateHash 的 Tool。按动作分批且单轮不得超过 maxToolsPerRound，然后重新提交复合提案。");
        var actions = root.putArray("actions");
        for (MissingCompositeArtifacts item : missing) {
            var row = actions.addObject();
            row.put("sequenceNo", item.sequenceNo());
            row.put("actionType", item.actionType().name());
            row.put("ruleCode", item.ruleCode());
            row.put("candidateHash", item.candidateHash());
            row.set("missingTools", mapper.valueToTree(item.missingTools()));
        }
        return "复合提案预校验未完成: " + jsonService.json(root);
    }

    private boolean validateBacktestArtifact(String outputJson, RuleCandidate candidate) {
        JsonNode result = jsonService.tree(outputJson);
        String status = result.path("executionStatus").asText(null);
        if (!StringUtils.hasText(status) || "UNAVAILABLE".equals(status)) {
            throw new IllegalArgumentException("回测产物不可用或来自旧版禁用回测，不能创建规则变更提案");
        }
        int determinate = result.path("determinateSampleCount").asInt(-1);
        if (determinate < properties.getMinimumFeedbackCount()) {
            throw new IllegalArgumentException("可判定回测样本不足 " + properties.getMinimumFeedbackCount() + " 个");
        }
        boolean semantic = candidate != null && (candidate.executorType() == com.example.disclosurereview.rule.domain.RuleExecutorType.LLM_POLICY
                || candidate.executorType() == com.example.disclosurereview.rule.domain.RuleExecutorType.HYBRID);
        if (semantic && !Boolean.FALSE.equals(candidate.enabled()) && result.path("llmCallCount").asInt(0) < 1) {
            throw new IllegalArgumentException("语义候选规则没有有效的 LLM 回测调用");
        }
        return "PARTIAL".equals(status);
    }

    private boolean partialBacktest(String outputJson) {
        return "PARTIAL".equals(jsonService.tree(outputJson).path("executionStatus").asText());
    }

    private JsonNode compositeSnapshot(List<ActionPlan> actions) {
        var root = mapper.createObjectNode();
        var rows = root.putArray("actions");
        for (ActionPlan action : actions) {
            var row = rows.addObject();
            row.put("sequenceNo", action.sequenceNo());
            row.put("actionType", action.actionType().name());
            row.put("ruleCode", action.candidate().ruleCode());
            row.put("candidateHash", action.candidateHash());
            row.set("beforeRule", jsonService.tree(action.beforeJson()));
            row.set("afterRule", jsonService.tree(action.afterJson()));
        }
        return root;
    }

    private JsonNode aggregate(List<ActionPlan> actions, String field) {
        var root = mapper.createObjectNode();
        var rows = root.putArray("actions");
        String worstRisk = null;
        for (ActionPlan action : actions) {
            var row = rows.addObject();
            row.put("sequenceNo", action.sequenceNo());
            row.put("actionType", action.actionType().name());
            row.put("candidateHash", action.candidateHash());
            row.set(field, jsonService.tree(switch (field) {
                case "validation" -> action.validationJson();
                case "backtest" -> action.backtestJson();
                case "affectedScope" -> action.affectedScopeJson();
                default -> "{}";
            }));
            if ("backtest".equals(field)) {
                worstRisk = worstRisk(worstRisk, jsonService.tree(action.backtestJson()).path("riskLevel").asText(null));
            }
        }
        if ("backtest".equals(field) && worstRisk != null) root.put("riskLevel", worstRisk);
        return root;
    }

    private String worstRisk(String current, String candidate) {
        if (!StringUtils.hasText(candidate)) return current;
        if (!StringUtils.hasText(current)) return candidate;
        return riskRank(candidate) > riskRank(current) ? candidate : current;
    }

    private int riskRank(String value) {
        return switch (value) {
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            case "LOW" -> 1;
            default -> 0;
        };
    }

    @Transactional
    public void attachAgentCall(Long proposalId,
                                ModelCallRecordEntity successfulCall,
                                String provider,
                                String model,
                                String responseJson) {
        RuleChangeProposalEntity proposal = proposalRepository.findById(proposalId).orElse(null);
        if (proposal == null) return;
        if (successfulCall != null && successfulCall.getModelConfig() != null) {
            proposal.setAgentModelConfig(successfulCall.getModelConfig());
            RuleGovernanceRunEntity run = proposal.getGovernanceRun();
            if (run.getModelConfig() == null) {
                run.setModelConfig(successfulCall.getModelConfig());
                runRepository.save(run);
            }
        }
        proposal.setAgentProvider(provider);
        proposal.setAgentModel(model);
        proposal.setAgentResponseJson(responseJson);
        proposal.setUpdatedAt(Instant.now());
        proposalRepository.save(proposal);
        llmAttemptRepository.findByGovernanceRunIdAndGovernanceGroupIdOrderById(
                proposal.getGovernanceRun().getId(), proposal.getGovernanceGroup().getId()).forEach(attempt -> {
            attempt.setGovernanceProposalId(proposalId);
            llmAttemptRepository.save(attempt);
        });
        modelCallRepository.findByGovernanceRunIdAndGovernanceGroupIdOrderById(
                proposal.getGovernanceRun().getId(), proposal.getGovernanceGroup().getId()).forEach(call -> {
            call.setGovernanceProposalId(proposalId);
            modelCallRepository.save(call);
        });
    }

    @Transactional
    public void attachAgentCallById(Long proposalId,
                                    Long successfulCallId,
                                    String provider,
                                    String model,
                                    String responseJson) {
        ModelCallRecordEntity call = successfulCallId == null ? null : modelCallRepository.findById(successfulCallId).orElse(null);
        attachAgentCall(proposalId, call, provider, model, responseJson);
    }

    private RuleGovernanceToolCallEntity requireArtifact(Long runId, Long groupId, String toolName, String candidateHash) {
        return findArtifact(runId, groupId, toolName, candidateHash)
                .orElseThrow(() -> new IllegalArgumentException("缺少与候选规则一致的服务端 Tool 结果: " + toolName));
    }

    private Optional<RuleGovernanceToolCallEntity> findArtifact(Long runId, Long groupId,
                                                                 String toolName, String candidateHash) {
        return toolCallRepository
                .findFirstByGovernanceRun_IdAndGovernanceGroup_IdAndToolNameAndCandidateHashAndCallStatusOrderByIdDesc(
                        runId, groupId, toolName, candidateHash, "SUCCESS");
    }

    private boolean requiresRuleCandidate(ProposalType type) {
        return type == ProposalType.UPDATE_RULE || type == ProposalType.DISABLE_RULE
                || type == ProposalType.CREATE_RULE || type == ProposalType.CREATE_EXCEPTION;
    }

    private void requireProposalMatchesIntent(RuleFeedbackGovernanceGroupEntity group, ProposalType type) {
        if (!group.isRuleGap()) return;
        if (!List.of(ProposalType.CREATE_RULE, ProposalType.OPTIMIZATION_ADVICE,
                ProposalType.NO_ACTION).contains(type)) {
            throw new IllegalArgumentException("RULE_GAP 分组没有来源规则，只允许新增规则、优化建议或不处理提案");
        }
    }

    private void requireText(String value, String field) {
        if (!StringUtils.hasText(value)) throw new IllegalArgumentException(field + " is required");
    }

    private String proposalNo(Instant now) {
        return "RGP-" + DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC).format(now)
                + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private record ActionPlan(int sequenceNo,
                              ProposalType actionType,
                              RuleCandidate candidate,
                              com.example.disclosurereview.persistence.entity.ReviewRuleVersionEntity sourceRuleVersion,
                              String candidateHash,
                              String beforeJson,
                              String afterJson,
                              String compareJson,
                              String validationJson,
                              String backtestJson,
                              String affectedScopeJson) {}

    private record PreparedCompositeAction(int sequenceNo,
                                           ProposalType actionType,
                                           RuleCandidate candidate,
                                           JsonNode candidateRule,
                                           boolean creating,
                                           String candidateHash,
                                           Map<String, RuleGovernanceToolCallEntity> artifacts) {}

    private record MissingCompositeArtifacts(int sequenceNo,
                                             ProposalType actionType,
                                             String ruleCode,
                                             String candidateHash,
                                             List<String> missingTools) {}
}
