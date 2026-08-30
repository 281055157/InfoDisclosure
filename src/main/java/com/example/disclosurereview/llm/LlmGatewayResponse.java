package com.example.disclosurereview.llm;

import com.example.disclosurereview.persistence.entity.ModelCallRecordEntity;

public record LlmGatewayResponse<T>(
        T result,
        ModelCallRecordEntity modelCallRecord,
        String providerCode,
        String modelName,
        LlmUsage usage
) {
}
