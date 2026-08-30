package com.example.disclosurereview.governance;

import com.example.disclosurereview.governance.domain.*;
import com.example.disclosurereview.governance.persistence.entity.*;
import com.example.disclosurereview.governance.persistence.repository.*;
import com.example.disclosurereview.governance.service.RuleProposalReviewService;
import com.example.disclosurereview.persistence.entity.ReviewRuleDefinitionEntity;
import com.example.disclosurereview.persistence.entity.ReviewRuleVersionEntity;
import com.example.disclosurereview.persistence.repository.ReviewRuleDefinitionJpaRepository;
import com.example.disclosurereview.persistence.repository.ReviewRuleVersionJpaRepository;
import com.example.disclosurereview.service.AdminConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class RuleProposalReviewIntegrationTest {
    @Autowired private RuleProposalReviewService reviewService;
    @Autowired private RuleGovernanceRunJpaRepository runRepository;
    @Autowired private RuleFeedbackGovernanceGroupJpaRepository groupRepository;
    @Autowired private RuleChangeProposalJpaRepository proposalRepository;
    @Autowired private RuleChangeProposalActionJpaRepository actionRepository;
    @Autowired private ReviewRuleDefinitionJpaRepository definitionRepository;
    @Autowired private ReviewRuleVersionJpaRepository versionRepository;
    @Autowired private AdminConfigService adminConfigService;
    @Autowired private ObjectMapper mapper;

    @Test
    void approvalCreatesDraftAndNeverChangesPublishedRuleDirectly() throws Exception {
        Fixture fixture = fixture();

        reviewService.approve(fixture.proposal().getId(), "reviewer-a", "方向正确，进入规则草稿审批");

        RuleChangeProposalEntity approved = proposalRepository.findById(fixture.proposal().getId()).orElseThrow();
        assertThat(approved.getProposalStatus()).isEqualTo(ProposalStatus.APPROVED);
        assertThat(approved.getReviewedBy()).isEqualTo("reviewer-a");
        assertThat(approved.getDraftRuleVersion()).isNotNull();
        ReviewRuleVersionEntity draft = versionRepository.findById(approved.getDraftRuleVersion().getId()).orElseThrow();
        assertThat(draft.getStatus()).isEqualTo("DRAFT");
        assertThat(draft.isActive()).isFalse();
        assertThat(draft.getSourceProposalId()).isEqualTo(approved.getId());
        ReviewRuleDefinitionEntity unchanged = definitionRepository.findById(fixture.definition().getId()).orElseThrow();
        assertThat(unchanged.getActiveVersionId()).isEqualTo(fixture.publishedVersion().getId());
        assertThat(unchanged.isEnabled()).isTrue();

        assertThatThrownBy(() -> reviewService.reject(approved.getId(), ProposalRejectionReason.CHANGE_TOO_BROAD,
                "reviewer-b", "并发重复审批"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("其他用户处理");
    }

    @Test
    void compositeApprovalCreatesDraftAndSecondConfirmationAppliesReplacementAtomically() throws Exception {
        Fixture fixture = fixture();
        RuleChangeProposalEntity composite = compositeProposal(fixture);

        reviewService.approve(composite.getId(), "reviewer-a", "组合方案通过");

        RuleChangeProposalEntity approved = proposalRepository.findById(composite.getId()).orElseThrow();
        assertThat(approved.getProposalStatus()).isEqualTo(ProposalStatus.APPROVED);
        assertThat(approved.getDraftRuleVersion()).isNotNull();
        assertThat(definitionRepository.findById(fixture.definition().getId()).orElseThrow().isEnabled()).isTrue();
        assertThat(actionRepository.findByProposal_IdOrderBySequenceNoAsc(composite.getId()))
                .extracting(RuleChangeProposalActionEntity::getActionStatus)
                .containsExactly(ProposalActionStatus.DISABLE_PENDING, ProposalActionStatus.DRAFT_CREATED);
        ReviewRuleVersionEntity draft = versionRepository.findById(approved.getDraftRuleVersion().getId()).orElseThrow();
        assertThat(draft.getStatus()).isEqualTo("DRAFT");
        assertThat(draft.isActive()).isFalse();
        assertThat(draft.getExecutorType()).isEqualTo("LLM_POLICY");

        reviewService.applyDisable(composite.getId(), "reviewer-a", "确认发布新规则并停用旧规则");
        assertThat(definitionRepository.findById(fixture.definition().getId()).orElseThrow().isEnabled()).isFalse();
        ReviewRuleVersionEntity publishedReplacement = versionRepository.findById(draft.getId()).orElseThrow();
        assertThat(publishedReplacement.getStatus()).isEqualTo("PUBLISHED");
        assertThat(publishedReplacement.isActive()).isTrue();
        ReviewRuleDefinitionEntity replacement = definitionRepository
                .findById(publishedReplacement.getRuleDefinition().getId()).orElseThrow();
        assertThat(replacement.isEnabled()).isTrue();
        assertThat(replacement.getActiveVersionId()).isEqualTo(publishedReplacement.getId());
        assertThat(actionRepository.findByProposal_IdOrderBySequenceNoAsc(composite.getId()))
                .extracting(RuleChangeProposalActionEntity::getActionStatus)
                .containsExactly(ProposalActionStatus.APPLIED, ProposalActionStatus.APPLIED);
        assertThat(proposalRepository.findById(composite.getId()).orElseThrow().getProposalStatus()).isEqualTo(ProposalStatus.APPLIED);
    }

    @Test
    void manuallyPublishingCompositeDraftEnablesReplacementBeforeOldRuleIsDisabled() throws Exception {
        Fixture fixture = fixture();
        RuleChangeProposalEntity composite = compositeProposal(fixture);
        reviewService.approve(composite.getId(), "reviewer-a", "组合方案通过");
        List<RuleChangeProposalActionEntity> actions = actionRepository
                .findByProposal_IdOrderBySequenceNoAsc(composite.getId());
        RuleChangeProposalActionEntity createAction = actions.get(1);

        adminConfigService.publishRuleVersion(
                createAction.getDraftRuleDefinition().getId(), createAction.getDraftRuleVersion().getId());

        ReviewRuleDefinitionEntity replacement = definitionRepository
                .findById(createAction.getDraftRuleDefinition().getId()).orElseThrow();
        assertThat(replacement.isEnabled()).isTrue();
        assertThat(replacement.getActiveVersionId()).isEqualTo(createAction.getDraftRuleVersion().getId());
        assertThat(definitionRepository.findById(fixture.definition().getId()).orElseThrow().isEnabled()).isTrue();
        assertThat(actionRepository.findByProposal_IdOrderBySequenceNoAsc(composite.getId()))
                .extracting(RuleChangeProposalActionEntity::getActionStatus)
                .containsExactly(ProposalActionStatus.DISABLE_PENDING, ProposalActionStatus.APPLIED);

        reviewService.applyDisable(composite.getId(), "reviewer-a", "确认停用旧规则");

        assertThat(definitionRepository.findById(fixture.definition().getId()).orElseThrow().isEnabled()).isFalse();
        assertThat(proposalRepository.findById(composite.getId()).orElseThrow().getProposalStatus())
                .isEqualTo(ProposalStatus.APPLIED);
    }

    private Fixture fixture() throws Exception {
        Instant now = Instant.now();
        String ruleCode = "GOV_APPROVAL_" + now.toEpochMilli();
        ReviewRuleDefinitionEntity definition = new ReviewRuleDefinitionEntity();
        definition.setRuleCode(ruleCode); definition.setRuleName("审批测试规则"); definition.setRuleType("REGEX");
        definition.setRuleCategory("REGEX"); definition.setPriority(100); definition.setEnabled(true);
        definition.setSeverity("MEDIUM"); definition.setConfidence(0.8); definition.setVersionCode("v1");
        definition.setCreatedAt(now); definition.setUpdatedAt(now); definition = definitionRepository.save(definition);

        ReviewRuleVersionEntity published = new ReviewRuleVersionEntity();
        published.setRuleDefinition(definition); published.setVersionCode(ruleCode + ":v1"); published.setVersionNumber(1);
        published.setExecutorType("REGEX"); published.setScopeJson("{\"documentCategories\":[\"PROTOCOL\"]}");
        published.setConditionJson("{\"patterns\":[\"旧表达式\"]}"); published.setActionJson("{\"severity\":\"MEDIUM\"}");
        published.setPromptJson("{}"); published.setStatus("PUBLISHED"); published.setActive(true);
        published.setCreatedAt(now); published.setUpdatedAt(now); published = versionRepository.save(published);
        definition.setActiveVersionId(published.getId()); definition = definitionRepository.save(definition);

        RuleGovernanceRunEntity run = new RuleGovernanceRunEntity();
        run.setRunNo("RGR-APPROVAL-" + now.toEpochMilli()); run.setTriggerType(GovernanceRunTriggerType.MANUAL);
        run.setStatus(GovernanceRunStatus.SUCCESS); run.setStartedAt(now); run.setFinishedAt(now);
        run.setCreatedGroupCount(1); run.setCreatedProposalCount(1); run.setCreatedAt(now); run.setUpdatedAt(now);
        run = runRepository.save(run);

        RuleFeedbackGovernanceGroupEntity group = new RuleFeedbackGovernanceGroupEntity();
        group.setGroupKey(ruleCode + "|1|PROTOCOL|投资协议书|FALSE_POSITIVE"); group.setRuleDefinition(definition);
        group.setRuleCode(ruleCode); group.setRuleVersionEntity(published); group.setRuleVersion("v1");
        group.setFeedbackType("FALSE_POSITIVE"); group.setDocumentCategory("PROTOCOL"); group.setDeclaredFileType("投资协议书");
        group.setStatus(GovernanceGroupStatus.PROPOSAL_CREATED); group.setFeedbackCount(3); group.setGovernanceRun(run);
        group.setLatestFeedbackAt(now); group.setCreatedAt(now); group.setUpdatedAt(now); group = groupRepository.save(group);

        var candidate = mapper.createObjectNode();
        candidate.put("ruleCode", ruleCode); candidate.put("ruleName", "审批测试规则");
        candidate.put("executorType", "REGEX"); candidate.put("priority", 100); candidate.put("enabled", true);
        candidate.set("scope", mapper.readTree("{\"documentCategories\":[\"PROTOCOL\"],\"documentTypes\":[\"投资协议书\"]}"));
        candidate.set("condition", mapper.readTree("{\"patterns\":[\"新表达式\"]}"));
        candidate.set("action", mapper.readTree("{\"severity\":\"MEDIUM\",\"confidence\":0.8}"));
        candidate.set("prompt", mapper.createObjectNode());
        RuleChangeProposalEntity proposal = new RuleChangeProposalEntity();
        proposal.setProposalNo("RGP-APPROVAL-" + now.toEpochMilli()); proposal.setProposalType(ProposalType.UPDATE_RULE);
        proposal.setProposalStatus(ProposalStatus.PENDING_REVIEW); proposal.setGovernanceGroup(group); proposal.setGovernanceRun(run);
        proposal.setRuleDefinition(definition); proposal.setRuleCode(ruleCode); proposal.setSourceRuleVersionEntity(published);
        proposal.setSourceRuleVersion("v1"); proposal.setRootCauseType(RootCauseType.RULE_SCOPE); proposal.setAgentConfidence(0.9);
        proposal.setProblemSummary("误报范围过宽"); proposal.setRootCauseAnalysis("文档类型范围过宽");
        proposal.setBeforeRuleSnapshotJson("{}"); proposal.setAfterRuleSnapshotJson(mapper.writeValueAsString(candidate));
        proposal.setHumanFollowUpRequired(false); proposal.setSubmittedAt(now); proposal.setCreatedAt(now); proposal.setUpdatedAt(now);
        proposal = proposalRepository.save(proposal);
        return new Fixture(definition, published, proposal);
    }

    private RuleChangeProposalEntity compositeProposal(Fixture fixture) throws Exception {
        Instant now = Instant.now();
        RuleFeedbackGovernanceGroupEntity group = fixture.proposal().getGovernanceGroup();
        RuleGovernanceRunEntity run = fixture.proposal().getGovernanceRun();
        RuleChangeProposalEntity proposal = new RuleChangeProposalEntity();
        proposal.setProposalNo("RGP-COMPOSITE-" + now.toEpochMilli());
        proposal.setProposalType(ProposalType.COMPOSITE_RULE_CHANGE);
        proposal.setProposalStatus(ProposalStatus.PENDING_REVIEW);
        proposal.setGovernanceGroup(group); proposal.setGovernanceRun(run);
        proposal.setRuleDefinition(fixture.definition()); proposal.setRuleCode(fixture.definition().getRuleCode());
        proposal.setSourceRuleVersionEntity(fixture.publishedVersion()); proposal.setSourceRuleVersion("v1");
        proposal.setRootCauseType(RootCauseType.RULE_EXECUTOR); proposal.setAgentConfidence(0.9);
        proposal.setProblemSummary("正则无法识别否定语境"); proposal.setRootCauseAnalysis("执行器能力不足");
        proposal.setChangeReason("停用旧正则并新建语义规则");
        proposal.setExpectedEffect("减少否定语境误报"); proposal.setRiskDescription("LLM规则需人工发布后观察效果");
        proposal.setBeforeRuleSnapshotJson("{}"); proposal.setAfterRuleSnapshotJson("{}");
        proposal.setChangeContentJson("{}"); proposal.setSubmittedAt(now); proposal.setCreatedAt(now); proposal.setUpdatedAt(now);
        proposal = proposalRepository.save(proposal);

        var disabled = mapper.createObjectNode();
        disabled.put("ruleCode", fixture.definition().getRuleCode()); disabled.put("ruleName", "审批测试规则");
        disabled.put("executorType", "REGEX"); disabled.put("priority", 100); disabled.put("enabled", false);
        disabled.set("scope", mapper.readTree("{\"documentCategories\":[\"PROTOCOL\"]}"));
        disabled.set("condition", mapper.readTree("{\"pattern\":\"旧表达式\"}"));
        disabled.set("action", mapper.readTree("{\"severity\":\"MEDIUM\",\"confidence\":0.8}"));
        disabled.set("prompt", mapper.createObjectNode());
        saveAction(proposal, 1, ProposalType.DISABLE_RULE, fixture.definition().getRuleCode(),
                fixture.publishedVersion(), disabled, now);

        var llm = mapper.createObjectNode();
        llm.put("ruleCode", fixture.definition().getRuleCode() + "_LLM"); llm.put("ruleName", "审批测试语义规则");
        llm.put("executorType", "LLM_POLICY"); llm.put("priority", 100); llm.put("enabled", true);
        llm.set("scope", mapper.readTree("{\"documentCategories\":[\"PROTOCOL\"]}"));
        llm.set("condition", mapper.readTree("{\"minConfidence\":0.8}"));
        llm.set("action", mapper.readTree("{\"issueType\":\"CONTENT_LOGIC_CONFLICT\",\"severity\":\"HIGH\",\"confidence\":0.8}"));
        llm.set("prompt", mapper.readTree("{\"reviewGoal\":\"识别正向保本承诺\",\"criteria\":\"否定语境不违规\"}"));
        saveAction(proposal, 2, ProposalType.CREATE_RULE, llm.path("ruleCode").asText(), null, llm, now);
        return proposal;
    }

    private void saveAction(RuleChangeProposalEntity proposal,
                            int sequence,
                            ProposalType actionType,
                            String ruleCode,
                            ReviewRuleVersionEntity source,
                            com.fasterxml.jackson.databind.JsonNode after,
                            Instant now) throws Exception {
        RuleChangeProposalActionEntity action = new RuleChangeProposalActionEntity();
        action.setProposal(proposal); action.setSequenceNo(sequence); action.setActionType(actionType);
        action.setActionStatus(ProposalActionStatus.PENDING_REVIEW); action.setRuleCode(ruleCode);
        action.setSourceRuleVersion(source); action.setBeforeRuleSnapshotJson("{}");
        action.setAfterRuleSnapshotJson(mapper.writeValueAsString(after));
        action.setCreatedAt(now); action.setUpdatedAt(now);
        actionRepository.save(action);
    }

    private record Fixture(ReviewRuleDefinitionEntity definition, ReviewRuleVersionEntity publishedVersion,
                           RuleChangeProposalEntity proposal) {}
}
