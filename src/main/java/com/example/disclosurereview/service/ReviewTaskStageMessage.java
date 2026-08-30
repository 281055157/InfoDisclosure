package com.example.disclosurereview.service;

import com.example.disclosurereview.model.ReviewStage;

public record ReviewTaskStageMessage(
        Long eventId,
        Long taskId,
        ReviewStage stage,
        String eventType,
        int attempt
) {
}
