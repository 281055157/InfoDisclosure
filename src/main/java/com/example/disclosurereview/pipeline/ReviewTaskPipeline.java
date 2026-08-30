package com.example.disclosurereview.pipeline;

import com.example.disclosurereview.model.ReviewStage;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class ReviewTaskPipeline {

    private final List<ReviewStageHandler> handlers;

    public ReviewTaskPipeline(List<ReviewStageHandler> handlers) {
        this.handlers = handlers.stream()
                .sorted(Comparator.comparingInt(ReviewStageHandler::order))
                .toList();
    }

    public void run(Long taskId, boolean retry, ReviewStage requestedStage) {
        ReviewPipelineContext context = new ReviewPipelineContext(taskId, retry, requestedStage);
        for (ReviewStageHandler handler : handlers) {
            if (retry && handler.stage().ordinal() < requestedStage.ordinal()) {
                continue;
            }
            StageResult result = handler.handle(context);
            if (result.terminal()) {
                return;
            }
        }
    }

    public StageResult handle(ReviewPipelineContext context) {
        return handlers.stream()
                .filter(handler -> handler.stage() == context.getRequestedStage())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No review stage handler for " + context.getRequestedStage()))
                .handle(context);
    }
}
