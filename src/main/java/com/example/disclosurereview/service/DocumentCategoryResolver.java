package com.example.disclosurereview.service;

import com.example.disclosurereview.model.DocumentCategory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class DocumentCategoryResolver {

    public DocumentCategory resolve(DocumentCategory requestedCategory, String b9Value) {
        DocumentCategory requested = requestedCategory == null
                ? DocumentCategory.AUTO
                : requestedCategory;
        if (requested != DocumentCategory.AUTO) {
            return requested;
        }
        return StringUtils.hasText(b9Value)
                ? DocumentCategory.ANNOUNCEMENT
                : DocumentCategory.PROTOCOL;
    }
}
