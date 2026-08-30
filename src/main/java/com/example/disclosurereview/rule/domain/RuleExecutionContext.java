package com.example.disclosurereview.rule.domain;

import com.example.disclosurereview.model.DocumentCategory;
import com.example.disclosurereview.model.DocumentPage;
import com.example.disclosurereview.model.DocumentType;
import com.example.disclosurereview.model.Product;

import java.util.List;

public record RuleExecutionContext(
        Long taskId,
        List<DocumentPage> pages,
        String fileName,
        DocumentCategory documentCategory,
        DocumentType documentType,
        String declaredProductCode,
        String declaredDocumentType,
        String b9Value,
        Product targetProduct
) {
    public RuleExecutionContext {
        pages = pages == null ? List.of() : List.copyOf(pages);
    }

    public String allText() {
        StringBuilder sb = new StringBuilder();
        for (DocumentPage page : pages) {
            if (page.normalizedText() != null) {
                sb.append(page.normalizedText()).append('\n');
            }
        }
        return sb.toString();
    }
}
