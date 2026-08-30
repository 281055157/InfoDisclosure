package com.example.disclosurereview.governance.service;

import com.example.disclosurereview.config.FeedbackGovernanceProperties;
import com.example.disclosurereview.governance.domain.RuleCandidate;
import com.example.disclosurereview.model.DocumentPage;
import com.example.disclosurereview.persistence.entity.DocumentPageEntity;
import com.example.disclosurereview.persistence.entity.ReviewTaskEntity;
import com.example.disclosurereview.persistence.repository.DocumentPageJpaRepository;
import com.example.disclosurereview.persistence.repository.ReviewTaskJpaRepository;
import com.example.disclosurereview.rule.domain.*;
import com.example.disclosurereview.rule.executor.RuleExecutorRegistry;
import com.example.disclosurereview.strategy.DocumentTypeAliasResolver;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

@Service
public class RuleExecutionSandbox {
    private final ReviewTaskJpaRepository taskRepository;
    private final DocumentPageJpaRepository pageRepository;
    private final DocumentTypeAliasResolver documentTypeResolver;
    private final RuleExecutorRegistry executorRegistry;
    private final FeedbackGovernanceProperties properties;
    private final ObjectMapper mapper;

    public RuleExecutionSandbox(ReviewTaskJpaRepository taskRepository,
                                DocumentPageJpaRepository pageRepository,
                                DocumentTypeAliasResolver documentTypeResolver,
                                RuleExecutorRegistry executorRegistry,
                                FeedbackGovernanceProperties properties,
                                ObjectMapper mapper) {
        this.taskRepository = taskRepository;
        this.pageRepository = pageRepository;
        this.documentTypeResolver = documentTypeResolver;
        this.executorRegistry = executorRegistry;
        this.properties = properties;
        this.mapper = mapper;
    }

    public SandboxResult executeCandidate(RuleCandidate candidate, Long taskId) {
        ReviewTaskEntity task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("回测任务不存在: " + taskId));
        if (Boolean.FALSE.equals(candidate.enabled())) {
            return new SandboxResult(false, RuleExecutionStatus.SKIPPED, "候选规则已停用");
        }
        Boolean applies = applies(candidate.scope(), task);
        if (applies == null) return new SandboxResult(null, RuleExecutionStatus.INDETERMINATE, "无法确定 productTypes 适用范围");
        if (!applies) return new SandboxResult(false, RuleExecutionStatus.SKIPPED, "候选规则不适用于该样本");
        if ((candidate.executorType() == RuleExecutorType.LLM_POLICY || candidate.executorType() == RuleExecutorType.HYBRID)
                && !properties.getBacktest().isLlmEnabled()) {
            return new SandboxResult(null, RuleExecutionStatus.INDETERMINATE, "LLM_RULE_BACKTEST_DISABLED");
        }
        List<DocumentPage> pages = pageRepository.findByTaskIdOrderByPageNumber(taskId).stream()
                .map(this::page).toList();
        RuleExecutionContext context = new RuleExecutionContext(taskId, pages, task.getOriginalFileName(),
                task.getDocumentCategory(), documentTypeResolver.resolve(task.getDeclaredDocumentType()),
                task.getDeclaredProductCode(), task.getDeclaredDocumentType(), task.getB9Value(), null);
        try {
            RuleExecutionResult result = executorRegistry.get(candidate.executorType())
                    .execute(context, candidate.definition(), candidate.version(mapper));
            Boolean matched = switch (result.status()) {
                case HIT -> true;
                case NOT_HIT, SKIPPED -> false;
                case FAILED, INDETERMINATE -> null;
            };
            return new SandboxResult(matched, result.status(), result.detail());
        } catch (Exception e) {
            return new SandboxResult(null, RuleExecutionStatus.FAILED, e.getMessage());
        }
    }

    private DocumentPage page(DocumentPageEntity entity) {
        return new DocumentPage(entity.getPageNumber(), entity.getRawText(), entity.getNormalizedText());
    }

    private Boolean applies(JsonNode scope, ReviewTaskEntity task) {
        if (scope == null || !scope.isObject()) return true;
        if (!matches(scope.path("documentCategories"), task.getDocumentCategory() == null ? null : task.getDocumentCategory().name())) return false;
        if (!matchesAny(scope.path("documentTypes"), task.getDeclaredDocumentType(),
                documentTypeResolver.resolve(task.getDeclaredDocumentType()).name())) return false;
        if (!matches(scope.path("productCodes"), task.getDeclaredProductCode())) return false;
        if (scope.path("productTypes").isArray() && !scope.path("productTypes").isEmpty()) return null;
        return true;
    }

    private boolean matches(JsonNode configured, String actual) {
        if (!configured.isArray() || configured.isEmpty()) return true;
        if (!StringUtils.hasText(actual)) return false;
        String normalizedActual = normalizeCategory(actual);
        for (JsonNode value : configured) if (normalizedActual.equals(normalizeCategory(value.asText()))) return true;
        return false;
    }

    private String normalizeCategory(String value) {
        String normalized = value == null ? "" : value.strip().toUpperCase(Locale.ROOT);
        return "AGREEMENT".equals(normalized) ? "PROTOCOL" : normalized;
    }

    private boolean matchesAny(JsonNode configured, String... actualValues) {
        if (!configured.isArray() || configured.isEmpty()) return true;
        for (JsonNode expected : configured) {
            String e = expected.asText("").toLowerCase(Locale.ROOT);
            for (String actual : actualValues) {
                if (StringUtils.hasText(actual) && actual.toLowerCase(Locale.ROOT).contains(e)) return true;
            }
        }
        return false;
    }

    public record SandboxResult(Boolean matched, RuleExecutionStatus status, String detail) {}
}
