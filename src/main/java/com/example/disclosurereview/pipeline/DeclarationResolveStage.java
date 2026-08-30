package com.example.disclosurereview.pipeline;

import com.example.disclosurereview.model.ReviewStage;
import com.example.disclosurereview.persistence.entity.ReviewTaskEntity;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class DeclarationResolveStage implements ReviewStageHandler {

    private final ReviewStageSupport support;
    private final ReviewTaskContextStore contextStore;

    public DeclarationResolveStage(ReviewStageSupport support, ReviewTaskContextStore contextStore) {
        this.support = support;
        this.contextStore = contextStore;
    }

    @Override
    public ReviewStage stage() {
        return ReviewStage.DECLARATION_RESOLVING;
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    public StageResult handle(ReviewPipelineContext context) {
        try {
            support.resolveAndPersistDeclarations(context.getTaskId());
            ReviewTaskEntity task = support.getTask(context.getTaskId());
            Map<String, Object> declaration = new LinkedHashMap<>();
            declaration.put("documentCategory", task.getDocumentCategory().name());
            declaration.put("declaredProductCode", task.getDeclaredProductCode());
            declaration.put("declaredDocumentType", task.getDeclaredDocumentType());
            declaration.put("b9Value", task.getB9Value());
            contextStore.put(task.getId(), "declaration", declaration);
            return StageResult.completed(stage(), "Declaration resolved");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
