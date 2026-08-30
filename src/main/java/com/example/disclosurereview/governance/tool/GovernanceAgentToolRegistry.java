package com.example.disclosurereview.governance.tool;

import com.example.disclosurereview.governance.persistence.entity.*;
import com.example.disclosurereview.governance.persistence.repository.*;
import com.example.disclosurereview.governance.service.GovernanceJsonService;
import com.example.disclosurereview.governance.service.GovernanceTraceService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

@Component
public class GovernanceAgentToolRegistry {
    private final Map<String, GovernanceAgentTool> tools;
    private final GovernanceToolSchemaValidator schemaValidator;
    private final RuleGovernanceToolCallJpaRepository callRepository;
    private final RuleGovernanceRunJpaRepository runRepository;
    private final RuleFeedbackGovernanceGroupJpaRepository groupRepository;
    private final RuleChangeProposalJpaRepository proposalRepository;
    private final GovernanceJsonService jsonService;
    private final ObjectMapper mapper;
    private final Validator validator;
    private final GovernanceTraceService traceService;
    private final GovernanceToolArgumentNormalizer argumentNormalizer;

    public GovernanceAgentToolRegistry(GovernanceAgentToolCatalog catalog,
                                       GovernanceToolSchemaValidator schemaValidator,
                                       RuleGovernanceToolCallJpaRepository callRepository,
                                       RuleGovernanceRunJpaRepository runRepository,
                                       RuleFeedbackGovernanceGroupJpaRepository groupRepository,
                                       RuleChangeProposalJpaRepository proposalRepository,
                                       GovernanceJsonService jsonService,
                                       ObjectMapper mapper,
                                       Validator validator,
                                       GovernanceTraceService traceService,
                                       GovernanceToolArgumentNormalizer argumentNormalizer) {
        Map<String, GovernanceAgentTool> registered = new LinkedHashMap<>();
        for (GovernanceAgentTool tool : catalog.tools()) {
            if (registered.put(tool.getName(), tool) != null) throw new IllegalStateException("重复治理 Tool: " + tool.getName());
        }
        this.tools = Collections.unmodifiableMap(registered);
        this.schemaValidator = schemaValidator;
        this.callRepository = callRepository;
        this.runRepository = runRepository;
        this.groupRepository = groupRepository;
        this.proposalRepository = proposalRepository;
        this.jsonService = jsonService;
        this.mapper = mapper;
        this.validator = validator;
        this.traceService = traceService;
        this.argumentNormalizer = argumentNormalizer;
    }

    public List<ToolDefinition> definitions() {
        return tools.values().stream().map(tool -> new ToolDefinition(
                tool.getName(), tool.getDescription(), tool.getInputSchema())).toList();
    }

    public ToolExecutionResult execute(String name, JsonNode arguments, GovernanceToolExecutionContext context) {
        GovernanceTraceService.SpanScope span = traceService.openWithParent(
                context.governanceRunId(), context.governanceGroupId(), context.traceParentSpanId(),
                "TOOL_CALL", name, context.executionMode(), context.parallelGroup(),
                context.iterationNumber() * 100 + context.toolIndex(), context.iterationNumber(),
                null, null, java.util.Map.of("operator", context.operator(), "toolIndex", context.toolIndex()));
        try {
            ToolExecutionResult result = executeTraced(name, arguments, context, span);
            return result;
        } catch (RuntimeException e) {
            span.fail(e);
            throw e;
        } finally {
            span.close();
        }
    }

    private ToolExecutionResult executeTraced(String name, JsonNode arguments,
                                              GovernanceToolExecutionContext context,
                                              GovernanceTraceService.SpanScope span) {
        GovernanceAgentTool tool = Optional.ofNullable(tools.get(name))
                .orElseThrow(() -> new SecurityException("未注册的治理 Tool: " + name));
        GovernanceToolArgumentNormalizer.NormalizationResult normalization = argumentNormalizer.normalize(arguments);
        JsonNode effectiveArguments = normalization.arguments();
        String discriminator = tool.cacheDiscriminator(effectiveArguments, context);
        String argumentHash;
        if (discriminator == null || discriminator.isBlank()) {
            argumentHash = jsonService.hash(effectiveArguments);
        } else {
            ObjectNode cacheKey = mapper.createObjectNode();
            cacheKey.set("arguments", effectiveArguments);
            cacheKey.put("executionFingerprint", discriminator);
            argumentHash = jsonService.hash(cacheKey);
        }
        RuleGovernanceToolCallEntity log = null;
        try {
            validateArguments(tool, effectiveArguments);
            if (tool.cacheable()) {
                Optional<RuleGovernanceToolCallEntity> cached = callRepository
                        .findFirstByGovernanceRun_IdAndGovernanceGroup_IdAndToolNameAndArgumentHashAndCallStatusOrderByIdDesc(
                                context.governanceRunId(), context.governanceGroupId(), name, argumentHash, "SUCCESS");
                if (cached.isPresent()) {
                    span.finish("CACHED", com.example.disclosurereview.llm.LlmUsage.empty(), null);
                    return new ToolExecutionResult(jsonService.tree(cached.get().getOutputJson()),
                            cached.get().getCandidateHash(), cached.get().getProposal() == null ? null : cached.get().getProposal().getId());
                }
            }

            long started = System.nanoTime();
            log = baseLog(name, effectiveArguments, argumentHash, context);
            ToolExecutionResult result = withArgumentRepairs(tool.execute(effectiveArguments, context), normalization.repairs());
            log.setCallStatus("SUCCESS");
            log.setOutputJson(jsonService.json(result.output()));
            log.setCandidateHash(result.candidateHash());
            if (result.proposalId() != null) proposalRepository.findById(result.proposalId()).ifPresent(log::setProposal);
            log.setDurationMs(elapsed(started));
            callRepository.save(log);
            span.success();
            return result;
        } catch (RuntimeException e) {
            if (log == null) {
                try {
                    log = baseLog(name, effectiveArguments, argumentHash, context);
                } catch (RuntimeException ignored) {
                    throw e;
                }
            }
            log.setCallStatus("FAILED");
            log.setErrorMessage(safeError(e));
            if (log.getDurationMs() == null) log.setDurationMs(0L);
            callRepository.save(log);
            throw e;
        }
    }

    public boolean registered(String name) { return tools.containsKey(name); }
    public boolean parallelSafe(String name) {
        GovernanceAgentTool tool = tools.get(name);
        return tool != null && tool.parallelSafe();
    }

    private void validateArguments(GovernanceAgentTool tool, JsonNode arguments) {
        List<String> schemaErrors = schemaValidator.validate(tool.getInputSchema(), arguments);
        if (!schemaErrors.isEmpty()) throw invalidArguments(tool, String.join("; ", schemaErrors), arguments);
        try {
            Object request = mapper.treeToValue(arguments, tool.getInputType());
            Set<ConstraintViolation<Object>> violations = validator.validate(request);
            if (!violations.isEmpty()) {
                throw invalidArguments(tool, violations.stream()
                        .map(v -> v.getPropertyPath() + " " + v.getMessage()).sorted().reduce((a, b) -> a + "; " + b).orElse("参数无效"), arguments);
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw invalidArguments(tool, "Tool 参数解析失败: " + e.getMessage(), arguments);
        }
    }

    private IllegalArgumentException invalidArguments(GovernanceAgentTool tool, String error, JsonNode arguments) {
        return new IllegalArgumentException(error + "；修复提示：请按该 Tool 的最小合法参数重新提交，示例 "
                + minimalExample(tool.getName(), tool.getInputSchema(), arguments)
                + "。不要在 thoughtSummary 中复述历史参数错误。");
    }

    private String minimalExample(String toolName, JsonNode schema, JsonNode arguments) {
        ObjectNode example = mapper.createObjectNode();
        if (schema != null && schema.path("required").isArray()) {
            for (JsonNode field : schema.path("required")) {
                String name = field.asText();
                JsonNode definition = schema.path("properties").path(name);
                example.set(name, placeholder(name, definition));
            }
        }
        if (example.has("candidateRule")) {
            example.set("candidateRule", candidateExample(arguments.path("candidateRule")));
        }
        if ("proposeCompositeRuleChange".equals(toolName)) {
            ArrayNode actions = mapper.createArrayNode();
            ObjectNode disable = mapper.createObjectNode();
            disable.put("actionType", "DISABLE_RULE");
            disable.put("ruleCode", "源规则编码");
            disable.put("sourceRuleVersionId", 0);
            ObjectNode disableCandidate = GovernanceRuleCandidateContract.regexExample(mapper);
            disableCandidate.put("enabled", false);
            disable.set("candidateRule", disableCandidate);
            ObjectNode create = mapper.createObjectNode();
            create.put("actionType", "CREATE_RULE");
            create.set("candidateRule", GovernanceRuleCandidateContract.llmPolicyExample(mapper));
            actions.add(disable); actions.add(create);
            example.set("actions", actions);
        }
        try { return mapper.writeValueAsString(example); }
        catch (Exception e) { return "{}"; }
    }

    private ObjectNode candidateExample(JsonNode candidate) {
        return "REGEX".equalsIgnoreCase(candidate.path("executorType").asText())
                ? GovernanceRuleCandidateContract.regexExample(mapper)
                : GovernanceRuleCandidateContract.llmPolicyExample(mapper);
    }

    private ToolExecutionResult withArgumentRepairs(ToolExecutionResult result, List<String> repairs) {
        if (repairs.isEmpty() || !(result.output() instanceof ObjectNode output)) return result;
        ObjectNode enriched = output.deepCopy();
        enriched.set("argumentRepairs", mapper.valueToTree(repairs));
        return new ToolExecutionResult(enriched, result.candidateHash(), result.proposalId());
    }

    private JsonNode placeholder(String name, JsonNode definition) {
        String type = definition == null ? "" : definition.path("type").asText();
        return switch (type) {
            case "integer" -> mapper.getNodeFactory().numberNode(0);
            case "number" -> mapper.getNodeFactory().numberNode(0.8);
            case "boolean" -> mapper.getNodeFactory().booleanNode(false);
            case "array" -> mapper.createArrayNode();
            case "object" -> mapper.createObjectNode();
            default -> mapper.getNodeFactory().textNode(switch (name) {
                case "rootCauseType" -> "RULE_EXECUTOR";
                case "problemSummary" -> "问题摘要";
                case "rootCauseAnalysis" -> "根因分析";
                case "changeReason" -> "变更原因";
                case "expectedEffect" -> "预期效果";
                case "riskDescription" -> "风险说明";
                case "optimizationCategory" -> "EXECUTOR_UPGRADE";
                case "optimizationAdvice" -> "优化建议";
                default -> "value";
            });
        };
    }

    private RuleGovernanceToolCallEntity baseLog(String name,
                                                  JsonNode arguments,
                                                  String argumentHash,
                                                  GovernanceToolExecutionContext context) {
        RuleGovernanceRunEntity run = runRepository.findById(context.governanceRunId())
                .orElseThrow(() -> new IllegalArgumentException("治理运行不存在"));
        RuleFeedbackGovernanceGroupEntity group = groupRepository.findById(context.governanceGroupId())
                .orElseThrow(() -> new IllegalArgumentException("治理分组不存在"));
        if (!group.getGovernanceRun().getId().equals(run.getId())) throw new SecurityException("治理运行与分组不匹配");
        RuleGovernanceToolCallEntity log = new RuleGovernanceToolCallEntity();
        log.setGovernanceRun(run); log.setGovernanceGroup(group); log.setIterationNumber(context.iterationNumber());
        log.setToolIndex(context.toolIndex()); log.setExecutionMode(context.executionMode());
        log.setParallelGroup(context.parallelGroup());
        log.setToolName(name); log.setArgumentHash(argumentHash); log.setInputJson(jsonService.json(arguments));
        log.setCallStatus("PROCESSING"); log.setCreatedAt(Instant.now());
        return log;
    }

    private long elapsed(long started) { return Math.max(0, (System.nanoTime() - started) / 1_000_000); }
    private String safeError(Throwable error) {
        String message = error.getMessage();
        if (message == null) return error.getClass().getSimpleName();
        return message.length() > 2000 ? message.substring(0, 2000) : message;
    }

    public record ToolDefinition(String name, String description, JsonNode inputSchema) {}
}
