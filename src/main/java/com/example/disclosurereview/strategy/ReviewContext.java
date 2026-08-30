package com.example.disclosurereview.strategy;

import com.example.disclosurereview.model.DocumentCategory;
import com.example.disclosurereview.model.DocumentPage;
import com.example.disclosurereview.model.DocumentType;
import com.example.disclosurereview.model.Product;

import java.util.List;

/** 策略审核所需的上下文，不改变原有上传接口。 */
public record ReviewContext(
        List<DocumentPage> pages,
        String fileName,
        DocumentCategory documentCategory,
        String declaredProductCode,
        String declaredDocumentTypeText,
        DocumentType declaredDocumentType,
        DocumentType candidateDocumentType,
        String b9Value,
        Product targetProduct,
        List<String> targetBankNames
) {
    public ReviewContext {
        pages = pages == null ? List.of() : List.copyOf(pages);
        targetBankNames = targetBankNames == null ? List.of() : List.copyOf(targetBankNames);
    }

    public String fullText() {
        return pages.stream()
                .map(DocumentPage::normalizedText)
                .filter(t -> t != null && !t.isBlank())
                .reduce("", (a, b) -> a + "\n" + b);
    }
}
