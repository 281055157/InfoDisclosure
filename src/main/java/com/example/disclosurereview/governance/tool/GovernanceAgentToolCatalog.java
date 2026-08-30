package com.example.disclosurereview.governance.tool;

import com.example.disclosurereview.governance.domain.*;
import com.example.disclosurereview.governance.persistence.entity.RuleChangeProposalEntity;
import com.example.disclosurereview.governance.service.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.BiFunction;

@Component
public class GovernanceAgentToolCatalog {
    private final GovernanceToolDataService data;
    private final FeedbackGovernanceGroupService groupService;
    private final RuleCandidateValidationService validationService;
    private final RuleBacktestService backtestService;
    private final RuleProposalService proposalService;
    private final GovernanceJsonService jsonService;
    private final ObjectMapper mapper;

    public GovernanceAgentToolCatalog(GovernanceToolDataService data,
                                      FeedbackGovernanceGroupService groupService,
                                      RuleCandidateValidationService validationService,
                                      RuleBacktestService backtestService,
                                      RuleProposalService proposalService,
                                      GovernanceJsonService jsonService,
                                      ObjectMapper mapper) {
        this.data = data;
        this.groupService = groupService;
        this.validationService = validationService;
        this.backtestService = backtestService;
        this.proposalService = proposalService;
        this.jsonService = jsonService;
        this.mapper = mapper;
    }

    public List<GovernanceAgentTool> tools() {
        List<GovernanceAgentTool> tools = new ArrayList<>();
        tools.add(tool("getGovernanceAnalysisBrief", "一次读取当前分组、样本、源规则版本、执行记录与历史治理结果；应作为 Agent 的第一个 Tool", GovernanceToolRequests.GroupRequest.class,
                schema(List.of("groupId"), "groupId", "integer"), true,
                (a, c) -> ToolExecutionResult.read(data.analysisBrief(requireGroup(a.path("groupId").asLong(), c)))));
        tools.add(tool("getFeedbackGroup", "读取当前反馈治理分组", GovernanceToolRequests.GroupRequest.class,
                schema(List.of("groupId"), "groupId", "integer"), true,
                (a, c) -> ToolExecutionResult.read(data.group(requireGroup(a.path("groupId").asLong(), c)))));
        tools.add(tool("getFeedbackSamples", "读取分组内的误报或漏报样本，不返回 PDF 全文", GovernanceToolRequests.FeedbackSamplesRequest.class,
                schema(List.of("groupId"), "groupId", "integer", "limit", "integer"), true,
                (a, c) -> ToolExecutionResult.read(data.feedbackSamples(requireGroup(a.path("groupId").asLong(), c), a.path("limit").asInt(10)))));
        tools.add(tool("getRuleDefinition", "读取当前分组规则定义", GovernanceToolRequests.RuleDefinitionRequest.class,
                schema(List.of("ruleCode"), "ruleCode", "string"), true,
                (a, c) -> ToolExecutionResult.read(data.ruleDefinition(requireRule(a.path("ruleCode").asText(), c)))));
        tools.add(tool("getRuleVersion", "读取规则指定的版本号；version 必须使用 getFeedbackGroup 返回的 ruleVersionNumber，不是 ruleVersionId", GovernanceToolRequests.RuleVersionRequest.class,
                schema(List.of("ruleCode", "version"), "ruleCode", "string", "version", "integer"), true,
                (a, c) -> ToolExecutionResult.read(data.ruleVersion(requireRule(a.path("ruleCode").asText(), c), a.path("version").asInt()))));
        tools.add(tool("getRuleExecutionRecords", "读取样本任务中的规则执行记录；version 必须使用 ruleVersionNumber，不是 ruleVersionId", GovernanceToolRequests.RuleExecutionRequest.class,
                schema(List.of("ruleCode", "version", "taskIds"), "ruleCode", "string", "version", "integer", "taskIds", "array"), true,
                (a, c) -> ToolExecutionResult.read(data.executions(requireRule(a.path("ruleCode").asText(), c), a.path("version").asInt(), longs(a.path("taskIds"))))));
        tools.add(tool("getDocumentContext", "读取反馈样本所在页及相邻页的受限文本", GovernanceToolRequests.DocumentContextRequest.class,
                schema(List.of("taskId", "pageNumber"), "taskId", "integer", "pageNumber", "integer", "includeAdjacentPages", "boolean"), true,
                (a, c) -> ToolExecutionResult.read(data.documentContext(requireSampleTask(a.path("taskId").asLong(), c),
                        a.path("pageNumber").asInt(), a.path("includeAdjacentPages").asBoolean(true)))));
        tools.add(tool("getHistoricalGovernanceDecisions", "按结构化字段查询历史治理 Memory", GovernanceToolRequests.MemoryRequest.class,
                schema(List.of(), "ruleCode", "string", "documentCategory", "string", "declaredFileType", "string", "rootCauseType", "string", "limit", "integer"), true,
                (a, c) -> ToolExecutionResult.read(data.memories(optionalRule(a.path("ruleCode").asText(null), c),
                        text(a, "documentCategory"), text(a, "declaredFileType"), rootCause(text(a, "rootCauseType")), a.path("limit").asInt(10)))));
        tools.add(tool("getSimilarAcceptedProposals", "查询同规则已接受的历史提案", GovernanceToolRequests.RuleDefinitionRequest.class,
                schema(List.of("ruleCode"), "ruleCode", "string"), true,
                (a, c) -> ToolExecutionResult.read(data.similarProposals(requireRule(a.path("ruleCode").asText(), c), true))));
        tools.add(tool("getSimilarRejectedProposals", "查询同规则已拒绝的历史提案", GovernanceToolRequests.RuleDefinitionRequest.class,
                schema(List.of("ruleCode"), "ruleCode", "string"), true,
                (a, c) -> ToolExecutionResult.read(data.similarProposals(requireRule(a.path("ruleCode").asText(), c), false))));

        tools.add(tool("validateRuleConfig", "校验完整候选规则；LLM_POLICY 的 prompt 必须位于 candidateRule 根级，与 condition/action 同级", GovernanceToolRequests.CandidateRequest.class,
                candidateSchema(schema(List.of("candidateRule"), "candidateRule", "object", "sourceRuleCode", "string", "creatingRule", "boolean")), true,
                (a, c) -> {
                    RuleCandidate candidate = candidate(a);
                    boolean creating = creatingRule(a, c);
                    String source = candidateSourceRule(a, c, creating);
                    CandidateValidationResult result = validationService.validate(candidate, source, creating);
                    ObjectNode output = mapper.valueToTree(result);
                    if (!result.valid()) {
                        output.put("repairHint", GovernanceRuleCandidateContract.promptPlacementHint());
                        output.set("minimalValidCandidateRule", GovernanceRuleCandidateContract.example(mapper, candidate.executorType()));
                    }
                    return ToolExecutionResult.candidate(output, result.candidateHash());
                }));
        tools.add(tool("compileRegex", "使用 RE2/J 编译候选正则", GovernanceToolRequests.RegexRequest.class,
                schema(List.of("patterns", "candidateHash"), "patterns", "array", "candidateHash", "string"), true,
                (a, c) -> {
                    var result = validationService.compileRegex(strings(a.path("patterns")));
                    if (result.stream().anyMatch(item -> !item.valid())) {
                        throw new IllegalArgumentException("候选正则编译失败: " + result.stream()
                                .filter(item -> !item.valid()).map(item -> item.pattern() + "=" + item.error())
                                .reduce((left, right) -> left + "; " + right).orElse("unknown"));
                    }
                    return ToolExecutionResult.candidate(mapper.valueToTree(result), text(a, "candidateHash"));
                }));
        tools.add(tool("checkRuleConflict", "确定性检查候选规则冲突", GovernanceToolRequests.CandidateRequest.class,
                candidateSchema(schema(List.of("candidateRule"), "candidateRule", "object", "sourceRuleCode", "string", "creatingRule", "boolean")), true,
                (a, c) -> {
                    boolean creating = creatingRule(a, c);
                    CandidateValidationResult result = validationService.validate(candidate(a), candidateSourceRule(a, c, creating), creating);
                    ObjectNode output = mapper.createObjectNode(); output.put("candidateHash", result.candidateHash());
                    output.set("conflicts", mapper.valueToTree(result.conflicts())); output.set("warnings", mapper.valueToTree(result.warnings()));
                    return ToolExecutionResult.candidate(output, result.candidateHash());
                }));
        tools.add(tool("runRuleBacktest", "在历史样本沙箱中运行候选规则；LLM_POLICY/HYBRID 会在 Tool 内自动完成分层采样、批量模型回测和证据验证，无需额外调用模型 Tool", GovernanceToolRequests.BacktestRequest.class,
                candidateSchema(schema(List.of("feedbackGroupId", "candidateRule"), "feedbackGroupId", "integer", "candidateRule", "object", "maximumSamples", "integer")), true,
                (a, c) -> {
                    long groupId = requireGroup(a.path("feedbackGroupId").asLong(), c);
                    RuleCandidate candidate = candidate(a);
                    RuleBacktestResult result = backtestService.run(groupId, candidate,
                            a.path("maximumSamples").asInt(100), c.governanceRunId(), c.iterationNumber());
                    backtestService.requireUsableForProposal(result, candidate);
                    return ToolExecutionResult.candidate(mapper.valueToTree(result), result.candidateHash());
                }, (a, c) -> backtestService.cacheFingerprint()));
        tools.add(tool("compareRuleVersions", "结构化比较源规则版本和候选规则", GovernanceToolRequests.CompareRequest.class,
                candidateSchema(schema(List.of("sourceRuleVersionId", "candidateRule"), "sourceRuleVersionId", "integer", "candidateRule", "object")), true,
                (a, c) -> ToolExecutionResult.candidate(data.compare(requireSourceVersion(a.path("sourceRuleVersionId").asLong(), c), candidate(a)),
                        jsonService.hash(candidate(a)))));
        tools.add(tool("estimateAffectedDocuments", "估算候选规则对近期文档的影响范围", GovernanceToolRequests.EstimateRequest.class,
                candidateSchema(schema(List.of("feedbackGroupId", "candidateRule"), "feedbackGroupId", "integer", "candidateRule", "object")), true,
                (a, c) -> ToolExecutionResult.candidate(data.estimate(requireGroup(a.path("feedbackGroupId").asLong(), c), candidate(a)),
                        jsonService.hash(candidate(a)))));

        tools.add(proposalTool("proposeRuleUpdate", ProposalType.UPDATE_RULE));
        tools.add(proposalTool("proposeRuleDisable", ProposalType.DISABLE_RULE));
        tools.add(proposalTool("proposeRuleCreate", ProposalType.CREATE_RULE));
        tools.add(proposalTool("proposeRuleException", ProposalType.CREATE_EXCEPTION));
        tools.add(compositeProposalTool());
        tools.add(proposalTool("proposeOptimizationAdvice", ProposalType.OPTIMIZATION_ADVICE));
        tools.add(proposalTool("proposeNoAction", ProposalType.NO_ACTION));
        return List.copyOf(tools);
    }

    private GovernanceAgentTool proposalTool(String name, ProposalType type) {
        return tool(name, "创建 " + type + " 治理提案；不会修改或发布生产规则", GovernanceToolRequests.ProposalRequest.class,
                proposalSchema(type), false, (a, c) -> {
                    requireGroup(a.path("governanceGroupId").asLong(), c);
                    ProposalCreateCommand command = new ProposalCreateCommand(type, rootCauseRequired(text(a, "rootCauseType")),
                            text(a, "problemSummary"), text(a, "rootCauseAnalysis"), text(a, "changeReason"),
                            text(a, "expectedEffect"), text(a, "riskDescription"), a.path("agentConfidence").asDouble(),
                            a.path("candidateRule").isObject() ? a.path("candidateRule") : null,
                            text(a, "optimizationCategory"), text(a, "optimizationAdvice"), text(a, "responsibleModule"),
                            text(a, "priority"), a.path("humanFollowUpRequired").asBoolean(false));
                    RuleChangeProposalEntity proposal = proposalService.create(c.governanceRunId(), c.governanceGroupId(), command, null);
                    ObjectNode output = mapper.createObjectNode(); output.put("proposalId", proposal.getId());
                    output.put("proposalNo", proposal.getProposalNo()); output.put("status", proposal.getProposalStatus().name());
                    return ToolExecutionResult.proposal(output, proposal.getId());
                });
    }

    private GovernanceAgentTool compositeProposalTool() {
        return tool("proposeCompositeRuleChange", "创建复合规则变更提案；用于同一治理结论包含多个有序规则动作",
                GovernanceToolRequests.CompositeProposalRequest.class, compositeProposalSchema(), false, (a, c) -> {
                    requireGroup(a.path("governanceGroupId").asLong(), c);
                    List<ProposalActionCommand> actions = new ArrayList<>();
                    for (JsonNode action : a.path("actions")) {
                        actions.add(new ProposalActionCommand(proposalType(text(action, "actionType")),
                                text(action, "ruleCode"),
                                action.path("sourceRuleVersionId").isIntegralNumber() ? action.path("sourceRuleVersionId").asLong() : null,
                                action.path("candidateRule").isObject() ? action.path("candidateRule") : null));
                    }
                    ProposalCreateCommand command = new ProposalCreateCommand(ProposalType.COMPOSITE_RULE_CHANGE,
                            rootCauseRequired(text(a, "rootCauseType")),
                            text(a, "problemSummary"), text(a, "rootCauseAnalysis"), text(a, "changeReason"),
                            text(a, "expectedEffect"), text(a, "riskDescription"), a.path("agentConfidence").asDouble(),
                            null, null, null, text(a, "responsibleModule"), text(a, "priority"),
                            a.path("humanFollowUpRequired").asBoolean(false), actions);
                    RuleChangeProposalEntity proposal = proposalService.create(c.governanceRunId(), c.governanceGroupId(), command, null);
                    ObjectNode output = mapper.createObjectNode(); output.put("proposalId", proposal.getId());
                    output.put("proposalNo", proposal.getProposalNo()); output.put("status", proposal.getProposalStatus().name());
                    output.put("actionCount", actions.size());
                    return ToolExecutionResult.proposal(output, proposal.getId());
                });
    }

    private GovernanceAgentTool tool(String name, String description, Class<?> type, JsonNode schema, boolean cacheable,
                                     BiFunction<JsonNode, GovernanceToolExecutionContext, ToolExecutionResult> action) {
        return tool(name, description, type, schema, cacheable, action, (arguments, context) -> "");
    }

    private GovernanceAgentTool tool(String name, String description, Class<?> type, JsonNode schema, boolean cacheable,
                                     BiFunction<JsonNode, GovernanceToolExecutionContext, ToolExecutionResult> action,
                                     BiFunction<JsonNode, GovernanceToolExecutionContext, String> cacheDiscriminator) {
        return new SimpleTool(name, description, type, schema, cacheable, action, cacheDiscriminator);
    }

    private ObjectNode schema(List<String> required, String... fields) {
        ObjectNode schema = mapper.createObjectNode(); schema.put("type", "object"); schema.put("additionalProperties", false);
        ArrayNode req = schema.putArray("required"); required.forEach(req::add); ObjectNode props = schema.putObject("properties");
        for (int i = 0; i + 1 < fields.length; i += 2) props.putObject(fields[i]).put("type", fields[i + 1]);
        return schema;
    }

    private JsonNode proposalSchema(ProposalType type) {
        if (type == ProposalType.OPTIMIZATION_ADVICE) {
            return schema(List.of("governanceGroupId", "rootCauseType", "problemSummary", "rootCauseAnalysis",
                            "optimizationCategory", "optimizationAdvice", "agentConfidence"),
                    "governanceGroupId", "integer", "rootCauseType", "string", "problemSummary", "string",
                    "rootCauseAnalysis", "string", "agentConfidence", "number",
                    "optimizationCategory", "string", "optimizationAdvice", "string", "responsibleModule", "string",
                    "priority", "string", "humanFollowUpRequired", "boolean");
        }
        if (type == ProposalType.NO_ACTION) {
            return schema(List.of("governanceGroupId", "rootCauseType", "problemSummary", "rootCauseAnalysis",
                            "agentConfidence"),
                    "governanceGroupId", "integer", "rootCauseType", "string", "problemSummary", "string",
                    "rootCauseAnalysis", "string", "agentConfidence", "number", "expectedEffect", "string",
                    "riskDescription", "string", "responsibleModule", "string", "priority", "string",
                    "humanFollowUpRequired", "boolean");
        }
        return candidateSchema(schema(List.of("governanceGroupId", "rootCauseType", "problemSummary", "rootCauseAnalysis",
                        "changeReason", "candidateRule", "expectedEffect", "riskDescription", "agentConfidence"),
                "governanceGroupId", "integer", "rootCauseType", "string", "problemSummary", "string",
                "rootCauseAnalysis", "string", "changeReason", "string", "candidateRule", "object",
                "expectedEffect", "string", "riskDescription", "string", "agentConfidence", "number",
                "responsibleModule", "string", "priority", "string", "humanFollowUpRequired", "boolean"));
    }

    private JsonNode compositeProposalSchema() {
        ObjectNode root = schema(List.of("governanceGroupId", "rootCauseType", "problemSummary",
                        "rootCauseAnalysis", "changeReason", "expectedEffect", "riskDescription",
                        "agentConfidence", "actions"),
                "governanceGroupId", "integer", "rootCauseType", "string", "problemSummary", "string",
                "rootCauseAnalysis", "string", "changeReason", "string", "expectedEffect", "string",
                "riskDescription", "string", "agentConfidence", "number", "actions", "array",
                "responsibleModule", "string", "priority", "string", "humanFollowUpRequired", "boolean");
        ObjectNode action = mapper.createObjectNode(); action.put("type", "object"); action.put("additionalProperties", false);
        ArrayNode required = action.putArray("required"); required.add("actionType"); required.add("candidateRule");
        ObjectNode props = action.putObject("properties");
        ObjectNode actionType = props.putObject("actionType").put("type", "string");
        actionType.putArray("enum").add("UPDATE_RULE").add("DISABLE_RULE").add("CREATE_RULE").add("CREATE_EXCEPTION");
        props.putObject("ruleCode").put("type", "string");
        props.putObject("sourceRuleVersionId").put("type", "integer");
        props.set("candidateRule", GovernanceRuleCandidateContract.schema(mapper));
        ((ObjectNode) root.path("properties").path("actions")).set("items", action);
        return root;
    }

    private ObjectNode candidateSchema(ObjectNode root) {
        ((ObjectNode) root.path("properties")).set("candidateRule", GovernanceRuleCandidateContract.schema(mapper));
        return root;
    }

    private RuleCandidate candidate(JsonNode args) { return RuleCandidate.from(args.path("candidateRule"), mapper); }
    private long requireGroup(long id, GovernanceToolExecutionContext c) { if (!Objects.equals(id, c.governanceGroupId())) throw new SecurityException("Tool 不允许访问其他治理分组"); return id; }
    private String requireRule(String code, GovernanceToolExecutionContext c) { String expected = groupRule(c); if (expected == null) throw new SecurityException("RULE_GAP 分组没有来源规则"); if (!Objects.equals(code, expected)) throw new SecurityException("Tool 不允许访问其他规则"); return code; }
    private String optionalRule(String code, GovernanceToolExecutionContext c) { return code == null || code.isBlank() ? groupRule(c) : requireRule(code, c); }
    private String groupRule(GovernanceToolExecutionContext c) { JsonNode value = data.group(c.governanceGroupId()).path("ruleCode"); return value.isMissingNode() || value.isNull() || value.asText().isBlank() ? null : value.asText(); }
    private boolean creatingRule(JsonNode arguments, GovernanceToolExecutionContext context) { return arguments.has("creatingRule") ? arguments.path("creatingRule").asBoolean(false) : groupRule(context) == null; }
    private String candidateSourceRule(JsonNode arguments, GovernanceToolExecutionContext context, boolean creating) {
        String groupRule = groupRule(context);
        String requested = text(arguments, "sourceRuleCode");
        if (groupRule == null) {
            if (!creating) throw new IllegalArgumentException("RULE_GAP 分组只能校验新增规则候选，creatingRule 必须为 true");
            if (requested != null && !requested.isBlank()) throw new SecurityException("RULE_GAP 分组没有来源规则，不能指定 sourceRuleCode");
            return null;
        }
        if (requested != null && !requested.isBlank()) requireRule(requested, context);
        return groupRule;
    }
    private long requireSourceVersion(long id, GovernanceToolExecutionContext c) { JsonNode source = data.group(c.governanceGroupId()).path("ruleVersionId"); if (!source.isIntegralNumber()) throw new SecurityException("RULE_GAP 分组没有源规则版本，不能比较规则版本"); long expected = source.asLong(); if (id != expected) throw new SecurityException("源版本不属于当前治理分组"); return id; }
    private long requireSampleTask(long taskId, GovernanceToolExecutionContext c) { if (!groupService.containsTask(c.governanceGroupId(), taskId)) throw new SecurityException("任务不属于当前反馈组"); return taskId; }
    private RootCauseType rootCause(String value) { if (value == null || value.isBlank()) return null; try { return RootCauseType.valueOf(value); } catch (Exception e) { throw new IllegalArgumentException("未知根因类型: " + value); } }
    private RootCauseType rootCauseRequired(String value) { RootCauseType result = rootCause(value); if (result == null) throw new IllegalArgumentException("rootCauseType is required"); return result; }
    private ProposalType proposalType(String value) { if (value == null || value.isBlank()) throw new IllegalArgumentException("actionType is required"); try { return ProposalType.valueOf(value); } catch (Exception e) { throw new IllegalArgumentException("未知动作类型: " + value); } }
    private List<Long> longs(JsonNode array) { List<Long> values = new ArrayList<>(); if (array.isArray()) array.forEach(v -> values.add(v.asLong())); return values; }
    private List<String> strings(JsonNode array) { List<String> values = new ArrayList<>(); if (array.isArray()) array.forEach(v -> values.add(v.asText())); return values; }
    private String text(JsonNode root, String field) { return root.has(field) && !root.path(field).isNull() ? root.path(field).asText(null) : null; }

    private record SimpleTool(String getName, String getDescription, Class<?> getInputType, JsonNode getInputSchema,
                              boolean cacheable,
                              BiFunction<JsonNode, GovernanceToolExecutionContext, ToolExecutionResult> action,
                              BiFunction<JsonNode, GovernanceToolExecutionContext, String> cacheDiscriminatorFunction)
            implements GovernanceAgentTool {
        @Override public ToolExecutionResult execute(JsonNode arguments, GovernanceToolExecutionContext context) { return action.apply(arguments, context); }
        @Override public String cacheDiscriminator(JsonNode arguments, GovernanceToolExecutionContext context) {
            return cacheDiscriminatorFunction.apply(arguments, context);
        }
    }
}
