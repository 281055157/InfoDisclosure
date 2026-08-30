package com.example.disclosurereview.governance;

import com.example.disclosurereview.config.FeedbackGovernanceProperties;
import com.example.disclosurereview.governance.domain.*;
import com.example.disclosurereview.governance.persistence.entity.RuleFeedbackGovernanceGroupEntity;
import com.example.disclosurereview.governance.persistence.entity.RuleGovernanceRunEntity;
import com.example.disclosurereview.governance.persistence.repository.*;
import com.example.disclosurereview.governance.service.*;
import com.example.disclosurereview.governance.tool.GovernanceRuleCandidateContract;
import com.example.disclosurereview.persistence.entity.ReviewRuleDefinitionEntity;
import com.example.disclosurereview.persistence.entity.ReviewRuleVersionEntity;
import com.example.disclosurereview.persistence.repository.LlmCallAttemptJpaRepository;
import com.example.disclosurereview.persistence.repository.ModelCallRecordJpaRepository;
import com.example.disclosurereview.service.AuditLogService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RuleProposalArtifactPlanningTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void compositeProposalReportsMissingArtifactsForEveryActionAtOnce() throws Exception {
        RuleFeedbackGovernanceGroupJpaRepository groups = mock(RuleFeedbackGovernanceGroupJpaRepository.class);
        RuleChangeProposalJpaRepository proposals = mock(RuleChangeProposalJpaRepository.class);
        RuleGovernanceToolCallJpaRepository toolCalls = mock(RuleGovernanceToolCallJpaRepository.class);
        RuleCandidateValidationService validation = mock(RuleCandidateValidationService.class);
        FeedbackGovernanceProperties properties = new FeedbackGovernanceProperties();
        properties.getAgent().setMaxToolsPerRound(6);
        RuleProposalService service = new RuleProposalService(
                groups,
                proposals,
                mock(RuleChangeProposalFeedbackJpaRepository.class),
                mock(RuleChangeProposalActionJpaRepository.class),
                toolCalls,
                mock(RuleGovernanceRunJpaRepository.class),
                new GovernanceActionPolicy(),
                validation,
                mock(RuleSnapshotService.class),
                mock(FeedbackGovernanceGroupService.class),
                mock(GovernanceMemoryService.class),
                new GovernanceJsonService(mapper),
                properties,
                mock(AuditLogService.class),
                mapper,
                mock(LlmCallAttemptJpaRepository.class),
                mock(ModelCallRecordJpaRepository.class));

        RuleGovernanceRunEntity run = new RuleGovernanceRunEntity();
        ReflectionTestUtils.setField(run, "id", 9L);
        ReviewRuleDefinitionEntity definition = new ReviewRuleDefinitionEntity();
        definition.setRuleCode("SOURCE_REGEX");
        definition.setRuleName("source");
        ReviewRuleVersionEntity version = new ReviewRuleVersionEntity();
        ReflectionTestUtils.setField(version, "id", 14L);
        version.setRuleDefinition(definition);
        version.setVersionCode("v2");
        version.setVersionNumber(2);
        version.setExecutorType("REGEX");
        version.setScopeJson("{\"documentCategories\":[\"PROTOCOL\"],\"documentTypes\":[],\"productCodes\":[],\"productTypes\":[]}");
        version.setConditionJson("{\"pattern\":\"保证本金\"}");
        version.setActionJson("{}");
        version.setPromptJson("{}");
        RuleFeedbackGovernanceGroupEntity group = new RuleFeedbackGovernanceGroupEntity();
        ReflectionTestUtils.setField(group, "id", 2L);
        group.setGovernanceRun(run);
        group.setRuleDefinition(definition);
        group.setRuleCode("SOURCE_REGEX");
        group.setRuleVersionEntity(version);
        group.setRuleVersion("v2");

        when(groups.findLockedById(2L)).thenReturn(Optional.of(group));
        when(proposals.existsByGovernanceGroup_IdAndProposalStatusIn(eq(2L), anyCollection())).thenReturn(false);
        when(validation.validate(any(RuleCandidate.class), eq("SOURCE_REGEX"), anyBoolean()))
                .thenAnswer(invocation -> new CandidateValidationResult(true,
                        invocation.getArgument(2, Boolean.class) ? "hash-create" : "hash-disable",
                        List.of(), List.of(), List.of()));
        when(toolCalls.findFirstByGovernanceRun_IdAndGovernanceGroup_IdAndToolNameAndCandidateHashAndCallStatusOrderByIdDesc(
                anyLong(), anyLong(), anyString(), anyString(), eq("SUCCESS"))).thenReturn(Optional.empty());

        ObjectNode disable = GovernanceRuleCandidateContract.regexExample(mapper);
        disable.put("ruleCode", "SOURCE_REGEX").put("enabled", false);
        ObjectNode create = GovernanceRuleCandidateContract.llmPolicyExample(mapper);
        create.put("ruleCode", "SOURCE_REGEX_LLM");
        ProposalCreateCommand command = new ProposalCreateCommand(
                ProposalType.COMPOSITE_RULE_CHANGE, RootCauseType.RULE_EXECUTOR,
                "正则无法识别否定上下文", "执行器缺少语义理解", "用 LLM 规则替换正则规则",
                "减少否定表达误报", "模型判定存在不确定性", 0.95,
                null, null, null, null, null, false,
                List.of(
                        new ProposalActionCommand(ProposalType.DISABLE_RULE, "SOURCE_REGEX", 14L, disable),
                        new ProposalActionCommand(ProposalType.CREATE_RULE, "SOURCE_REGEX_LLM", null, create)));

        IllegalArgumentException error = catchThrowableOfType(
                () -> service.create(9L, 2L, command, null), IllegalArgumentException.class);

        assertThat(error).isNotNull();
        assertThat(error.getMessage()).startsWith("复合提案预校验未完成: ");
        JsonNode repair = mapper.readTree(error.getMessage().substring(error.getMessage().indexOf('{')));
        assertThat(repair.path("errorCode").asText()).isEqualTo("MISSING_COMPOSITE_ARTIFACTS");
        assertThat(repair.path("maxToolsPerRound").asInt()).isEqualTo(6);
        assertThat(repair.path("actions")).hasSize(2);
        assertThat(repair.path("actions").get(0).path("candidateHash").asText()).isEqualTo("hash-disable");
        assertThat(repair.path("actions").get(0).path("missingTools"))
                .extracting(JsonNode::asText)
                .containsExactly("validateRuleConfig", "runRuleBacktest", "checkRuleConflict",
                        "estimateAffectedDocuments", "compareRuleVersions", "compileRegex");
        assertThat(repair.path("actions").get(1).path("candidateHash").asText()).isEqualTo("hash-create");
        assertThat(repair.path("actions").get(1).path("missingTools"))
                .extracting(JsonNode::asText)
                .containsExactly("validateRuleConfig", "runRuleBacktest", "checkRuleConflict",
                        "estimateAffectedDocuments");
    }
}
