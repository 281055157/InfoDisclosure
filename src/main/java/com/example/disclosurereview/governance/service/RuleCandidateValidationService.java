package com.example.disclosurereview.governance.service;

import com.example.disclosurereview.governance.domain.CandidateValidationResult;
import com.example.disclosurereview.governance.domain.RuleCandidate;
import com.example.disclosurereview.persistence.entity.ReviewRuleDefinitionEntity;
import com.example.disclosurereview.persistence.entity.ReviewRuleVersionEntity;
import com.example.disclosurereview.persistence.repository.ReviewRuleDefinitionJpaRepository;
import com.example.disclosurereview.persistence.repository.ReviewRuleVersionJpaRepository;
import com.example.disclosurereview.rule.domain.RuleExecutorType;
import com.example.disclosurereview.rule.domain.RuleValidationResult;
import com.example.disclosurereview.rule.executor.RuleExecutorRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.re2j.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;

@Service
public class RuleCandidateValidationService {
    private static final Set<String> DOCUMENT_CATEGORIES = Set.of("AUTO", "PROTOCOL", "ANNOUNCEMENT", "AGREEMENT");
    private final RuleExecutorRegistry registry;
    private final ReviewRuleDefinitionJpaRepository definitionRepository;
    private final ReviewRuleVersionJpaRepository versionRepository;
    private final GovernanceJsonService jsonService;
    private final ObjectMapper mapper;

    public RuleCandidateValidationService(RuleExecutorRegistry registry,
                                          ReviewRuleDefinitionJpaRepository definitionRepository,
                                          ReviewRuleVersionJpaRepository versionRepository,
                                          GovernanceJsonService jsonService,
                                          ObjectMapper mapper) {
        this.registry = registry;
        this.definitionRepository = definitionRepository;
        this.versionRepository = versionRepository;
        this.jsonService = jsonService;
        this.mapper = mapper;
    }

    public CandidateValidationResult validate(RuleCandidate candidate,
                                              String sourceRuleCode,
                                              boolean creatingRule) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (candidate == null) {
            return new CandidateValidationResult(false, null, List.of("candidateRule is required"), List.of(), List.of());
        }
        if (!StringUtils.hasText(candidate.ruleCode())) errors.add("ruleCode is required");
        if (!StringUtils.hasText(candidate.ruleName())) errors.add("ruleName is required");
        if (candidate.executorType() == null) errors.add("executorType is required or unsupported");
        if (candidate.executorType() == RuleExecutorType.JAVA_PLUGIN) errors.add("治理 Agent 不允许创建 JAVA_PLUGIN");
        validateObject("scope", candidate.scope(), errors);
        validateObject("condition", candidate.condition(), errors);
        validateObject("action", candidate.action(), errors);
        validateObject("prompt", candidate.prompt(), errors);
        validateScope(candidate.scope(), errors);
        if (candidate.priority() != null && (candidate.priority() < 0 || candidate.priority() > 10000)) {
            errors.add("priority 必须在 0 到 10000 之间");
        }
        if (candidate.executorType() != null && candidate.executorType() != RuleExecutorType.LLM_POLICY
                && candidate.executorType() != RuleExecutorType.HYBRID
                && candidate.prompt() != null && candidate.prompt().size() > 0) {
            errors.add("仅 LLM_POLICY/HYBRID 允许配置 prompt");
        }
        if (candidate.executorType() == RuleExecutorType.HYBRID
                && (candidate.condition() == null
                || !StringUtils.hasText(candidate.condition().path("locator").asText(null)))) {
            errors.add("HYBRID 必须在 condition.locator 指定确定性候选定位规则编码");
        }
        if (candidate.executorType() != null && candidate.executorType() != RuleExecutorType.JAVA_PLUGIN) {
            registry.find(candidate.executorType()).ifPresentOrElse(executor -> {
                RuleValidationResult result = executor.validate(candidate.version(mapper));
                if (!result.valid()) errors.addAll(result.errors());
            }, () -> errors.add("执行器未注册: " + candidate.executorType()));
        }

        Optional<ReviewRuleDefinitionEntity> sameCode = StringUtils.hasText(candidate.ruleCode())
                ? definitionRepository.findByRuleCode(candidate.ruleCode()) : Optional.empty();
        if (creatingRule && sameCode.isPresent()) errors.add("规则代码已存在: " + candidate.ruleCode());
        if (!creatingRule && StringUtils.hasText(sourceRuleCode)
                && !sourceRuleCode.equals(candidate.ruleCode())) errors.add("修改规则时不能变更 ruleCode");

        List<String> conflicts = deterministicConflicts(candidate, sourceRuleCode);
        if (!conflicts.isEmpty()) warnings.add("候选规则可能与现有规则重复，请人工检查");
        String candidateHash = jsonService.hash(candidate);
        return new CandidateValidationResult(errors.isEmpty(), candidateHash,
                List.copyOf(errors), List.copyOf(warnings), conflicts);
    }

    public List<RegexCompileResult> compileRegex(List<String> patterns) {
        if (patterns == null || patterns.isEmpty()) return List.of();
        return patterns.stream().map(pattern -> {
            if (!StringUtils.hasText(pattern)) return new RegexCompileResult(pattern, false, "正则不能为空");
            try {
                Pattern.compile(pattern);
                return new RegexCompileResult(pattern, true, null);
            } catch (Exception e) {
                return new RegexCompileResult(pattern, false, e.getMessage());
            }
        }).toList();
    }

    private List<String> deterministicConflicts(RuleCandidate candidate, String sourceRuleCode) {
        if (candidate.executorType() == null) return List.of();
        List<String> conflicts = new ArrayList<>();
        String candidateCondition = canonical(candidate.condition());
        String candidateScope = canonical(candidate.scope());
        for (ReviewRuleDefinitionEntity definition : definitionRepository.findAll()) {
            if (definition.getRuleCode().equals(sourceRuleCode)) continue;
            for (ReviewRuleVersionEntity version : versionRepository
                    .findByRuleDefinition_IdAndStatusOrderByVersionNumberDesc(definition.getId(), "PUBLISHED")) {
                if (!version.isActive() || !candidate.executorType().name().equalsIgnoreCase(version.getExecutorType())) continue;
                if (candidateCondition.equals(canonical(textTree(version.getConditionJson())))
                        && candidateScope.equals(canonical(textTree(version.getScopeJson())))) {
                    conflicts.add(definition.getRuleCode() + "@" + version.getVersionCode());
                }
            }
        }
        return List.copyOf(conflicts);
    }

    private void validateScope(JsonNode scope, List<String> errors) {
        if (scope == null || !scope.isObject()) return;
        JsonNode categories = scope.path("documentCategories");
        if (!categories.isMissingNode()) {
            if (!categories.isArray()) {
                errors.add("scope.documentCategories 必须是数组");
            } else {
                categories.forEach(value -> {
                    if (!DOCUMENT_CATEGORIES.contains(value.asText("").toUpperCase(Locale.ROOT))) {
                        errors.add("未知文档类别: " + value.asText());
                    }
                });
            }
        }
        for (String field : List.of("documentTypes", "productCodes", "productTypes")) {
            JsonNode value = scope.path(field);
            if (!value.isMissingNode() && !value.isArray()) errors.add("scope." + field + " 必须是数组");
        }
    }

    private void validateObject(String name, JsonNode node, List<String> errors) {
        if (node == null || !node.isObject()) errors.add(name + " 必须是 JSON 对象");
    }

    private JsonNode textTree(String value) {
        try { return mapper.readTree(value == null || value.isBlank() ? "{}" : value); }
        catch (Exception e) { return mapper.createObjectNode(); }
    }

    private String canonical(JsonNode node) {
        try { return mapper.writeValueAsString(node == null ? mapper.createObjectNode() : node); }
        catch (Exception e) { return "{}"; }
    }

    public record RegexCompileResult(String pattern, boolean valid, String error) {}
}
