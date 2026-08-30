package com.example.disclosurereview.pipeline;

import com.example.disclosurereview.model.ReviewStage;

public interface ReviewStageHandler {

    ReviewStage stage();

    int order();

    StageResult handle(ReviewPipelineContext context);
}
