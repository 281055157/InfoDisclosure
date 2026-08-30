package com.example.disclosurereview.pipeline;

import com.example.disclosurereview.model.ReviewStage;
import org.springframework.stereotype.Component;

@Component
public class PersistenceStage implements ReviewStageHandler {
    @Override
    public ReviewStage stage() {
        return ReviewStage.WAITING_MANUAL_REVIEW;
    }

    @Override
    public int order() {
        return 80;
    }

    @Override
    public StageResult handle(ReviewPipelineContext context) {
        return StageResult.terminal(stage(), "Persistence delegated to task worker");
    }
}
