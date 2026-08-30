package com.example.disclosurereview.strategy;

import com.example.disclosurereview.model.DocumentScope;
import com.example.disclosurereview.model.DocumentType;

/** 传给大模型的文件类型专用审核策略摘要。 */
public record StrategyReviewPolicy(
        DocumentType documentType,
        DocumentScope expectedScope,
        boolean concreteProductRequired,
        boolean multipleProductsAllowed,
        String promptPolicy
) {
}
