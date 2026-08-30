package com.example.disclosurereview.pipeline;

import com.example.disclosurereview.model.ReviewStage;

public record StageResult(
        ReviewStage stage,
        boolean success,
        boolean terminal,
        String detail
) {
    public static StageResult completed(ReviewStage stage, String detail) {
        return new StageResult(stage, true, false, detail);
    }

    public static StageResult terminal(ReviewStage stage, String detail) {
        return new StageResult(stage, true, true, detail);
    }
}
