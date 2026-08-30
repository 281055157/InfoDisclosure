package com.example.disclosurereview.pipeline;

import com.example.disclosurereview.model.DocumentPage;
import com.example.disclosurereview.model.ReviewStage;
import com.example.disclosurereview.model.ReviewTaskStatus;
import com.example.disclosurereview.persistence.entity.ReviewTaskEntity;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class DocumentParseStage implements ReviewStageHandler {

    private final ReviewStageSupport support;
    private final ReviewTaskContextStore contextStore;

    public DocumentParseStage(ReviewStageSupport support, ReviewTaskContextStore contextStore) {
        this.support = support;
        this.contextStore = contextStore;
    }

    @Override
    public ReviewStage stage() {
        return ReviewStage.DOCUMENT_PARSING;
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    public StageResult handle(ReviewPipelineContext context) {
        try {
            ReviewTaskEntity task = support.getTask(context.getTaskId());
            if (task.getStatus() == ReviewTaskStatus.CREATED) {
                support.transition(task.getId(), ReviewTaskStatus.FILE_STORED,
                        "File entered persistent review queue");
                support.updateStage(task.getId(), ReviewStage.FILE_STORED);
            }
            if (support.getTask(task.getId()).getStatus() != ReviewTaskStatus.PARSING) {
                support.transition(task.getId(), ReviewTaskStatus.PARSING,
                        context.isRetry() ? "Retry PDF parsing stage" : "Start PDF and parameter parsing");
            }
            support.updateStage(task.getId(), ReviewStage.DOCUMENT_PARSING);
            java.util.List<DocumentPage> pages = support.parseAndPersistPages(support.getTask(task.getId()));
            contextStore.remove(task.getId(), "declaration", "productMatch", "ruleReview",
                    "llmReview", "evidenceVerification", "resultMerge");
            contextStore.put(task.getId(), "documentParse", Map.of(
                    "pageCount", pages.size(),
                    "charCount", pages.stream()
                            .mapToInt(p -> p.normalizedText() == null ? 0 : p.normalizedText().length())
                            .sum()));
            return StageResult.completed(stage(), "PDF parsed and persisted");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
