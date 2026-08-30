package com.example.disclosurereview.rule.domain;

import java.util.List;

public record RuleScope(
        List<String> documentCategories,
        List<String> documentTypes,
        List<String> productCodes,
        List<String> productTypes
) {
    public RuleScope {
        documentCategories = documentCategories == null ? List.of() : List.copyOf(documentCategories);
        documentTypes = documentTypes == null ? List.of() : List.copyOf(documentTypes);
        productCodes = productCodes == null ? List.of() : List.copyOf(productCodes);
        productTypes = productTypes == null ? List.of() : List.copyOf(productTypes);
    }

    public static RuleScope all() {
        return new RuleScope(List.of(), List.of(), List.of(), List.of());
    }
}
