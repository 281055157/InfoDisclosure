package com.example.disclosurereview.service;

import com.example.disclosurereview.model.DocumentCategory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentCategoryResolverTest {

    private final DocumentCategoryResolver resolver = new DocumentCategoryResolver();

    @Test
    void resolvesAutoWithoutB9AsProtocol() {
        assertThat(resolver.resolve(DocumentCategory.AUTO, null))
                .isEqualTo(DocumentCategory.PROTOCOL);
        assertThat(resolver.resolve(null, "  "))
                .isEqualTo(DocumentCategory.PROTOCOL);
    }

    @Test
    void resolvesAutoWithB9AsAnnouncement() {
        assertThat(resolver.resolve(DocumentCategory.AUTO, "成立公告"))
                .isEqualTo(DocumentCategory.ANNOUNCEMENT);
    }

    @Test
    void preservesExplicitCategory() {
        assertThat(resolver.resolve(DocumentCategory.PROTOCOL, "成立公告"))
                .isEqualTo(DocumentCategory.PROTOCOL);
        assertThat(resolver.resolve(DocumentCategory.ANNOUNCEMENT, null))
                .isEqualTo(DocumentCategory.ANNOUNCEMENT);
    }
}
