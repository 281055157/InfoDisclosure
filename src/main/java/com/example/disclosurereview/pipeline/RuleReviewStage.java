package com.example.disclosurereview.pipeline;

import com.example.disclosurereview.model.DocumentPage;
import com.example.disclosurereview.model.DocumentType;
import com.example.disclosurereview.model.Product;
import com.example.disclosurereview.model.ReviewStage;
import com.example.disclosurereview.model.ReviewTaskStatus;
import com.example.disclosurereview.persistence.entity.ReviewTaskEntity;
import com.example.disclosurereview.repository.ProductRepository;
import com.example.disclosurereview.rule.RuleEngine;
import com.example.disclosurereview.rule.domain.PlannedRuleReviewOutcome;
import com.example.disclosurereview.strategy.DocumentTypeAliasResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class RuleReviewStage implements ReviewStageHandler {

    private final ReviewStageSupport support;
    private final ReviewTaskContextStore contextStore;
    private final ProductRepository productRepository;
    private final RuleEngine ruleEngine;
    private final DocumentTypeAliasResolver documentTypeResolver;

    public RuleReviewStage(ReviewStageSupport support,
                           ReviewTaskContextStore contextStore,
                           ProductRepository productRepository,
                           RuleEngine ruleEngine,
                           DocumentTypeAliasResolver documentTypeResolver) {
        this.support = support;
        this.contextStore = contextStore;
        this.productRepository = productRepository;
        this.ruleEngine = ruleEngine;
        this.documentTypeResolver = documentTypeResolver;
    }

    @Override
    public ReviewStage stage() {
        return ReviewStage.RULE_REVIEWING;
    }

    @Override
    public int order() {
        return 40;
    }

    @Override
    public StageResult handle(ReviewPipelineContext context) {
        Long taskId = context.getTaskId();
        if (support.getTask(taskId).getStatus() != ReviewTaskStatus.RULE_REVIEWING) {
            support.transition(taskId, ReviewTaskStatus.RULE_REVIEWING,
                    context.isRetry() ? "Retry rule review stage" : "Start rule review");
        }
        support.updateStage(taskId, ReviewStage.RULE_REVIEWING);
        ReviewTaskEntity task = support.getTask(taskId);
        List<DocumentPage> pages = support.persistedPages(taskId);
        if (pages.isEmpty()) {
            throw new IllegalStateException("PDF页面尚未持久化，无法执行规则审核");
        }
        Product matched = StringUtils.hasText(task.getDeclaredProductCode())
                ? productRepository.findAny(task.getDeclaredProductCode()).orElse(null)
                : null;
        DocumentType declaredType = documentTypeResolver.resolve(task.getDeclaredDocumentType());
        DocumentType preLlmCandidateType = documentTypeResolver.detectFromPages(pages);
        DocumentType strategyCandidate = declaredType != DocumentType.UNKNOWN ? declaredType : preLlmCandidateType;
        PlannedRuleReviewOutcome outcome = ruleEngine.reviewWithDeferredSemanticRules(
                pages,
                task.getDocumentCategory(),
                strategyCandidate,
                task.getOriginalFileName(),
                task.getDeclaredProductCode(),
                task.getDeclaredDocumentType(),
                task.getB9Value(),
                matched,
                taskId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ruleOutcome", outcome.ruleOutcome());
        data.put("semanticChecks", outcome.semanticChecks());
        data.put("declaredDocumentType", declaredType.name());
        data.put("preLlmCandidateType", preLlmCandidateType.name());
        data.put("strategyCandidateType", strategyCandidate.name());
        contextStore.put(taskId, "ruleReview", data);
        return StageResult.completed(stage(), "Rule review planned; semantic checks deferred=" + outcome.semanticChecks().size());
    }
}
