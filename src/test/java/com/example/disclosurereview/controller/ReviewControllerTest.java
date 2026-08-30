package com.example.disclosurereview.controller;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewControllerTest {

    @Test
    void encodesChineseFileNameInContentDisposition() {
        String header = ReviewController.inlineContentDisposition("QQGPJUSDRKN_产品说明书.pdf").toString();

        assertThat(header).startsWith("inline;");
        assertThat(header).contains("filename*=UTF-8''");
        assertThat(header).contains("%E4%BA%A7%E5%93%81%E8%AF%B4%E6%98%8E%E4%B9%A6.pdf");
        assertThat(header).doesNotContain("产品说明书");
    }

    @Test
    void sanitizesUnsafeHeaderFileNameParts() {
        String fileName = ReviewController.sanitizeHeaderFileName("..\\path/产品\"说明书\r\n.pdf");

        assertThat(fileName).isEqualTo("产品说明书.pdf");
    }
}
