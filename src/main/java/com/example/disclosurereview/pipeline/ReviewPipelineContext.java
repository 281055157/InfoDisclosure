package com.example.disclosurereview.pipeline;

import com.example.disclosurereview.model.ReviewStage;

public class ReviewPipelineContext {

    private final Long taskId;
    private final boolean retry;
    private final ReviewStage requestedStage;

    public ReviewPipelineContext(Long taskId, boolean retry, ReviewStage requestedStage) {
        this.taskId = taskId;
        this.retry = retry;
        this.requestedStage = requestedStage;
    }

    public Long getTaskId() {
        return taskId;
    }

    public boolean isRetry() {
        return retry;
    }

    public ReviewStage getRequestedStage() {
        return requestedStage;
    }
}
