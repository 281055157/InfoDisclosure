package com.example.disclosurereview.parser;

import com.example.disclosurereview.TestPdfFactory;
import com.example.disclosurereview.exception.PdfParseException;
import com.example.disclosurereview.model.DocumentPage;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PdfDocumentParserTest {

    private final PdfDocumentParser parser = new PdfDocumentParser();

    @Test
    void parsesPagesWithPageNumbers() {
        try (InputStream in = TestPdfFactory.pdfWithPages(
                "产品代码：SGN22555\n示例理财丙宁欣天天鎏金现金管理类理财产品3号",
                "第二页内容 投资协议书")) {
            List<DocumentPage> pages = parser.parse(in);
            assertThat(pages).hasSize(2);
            assertThat(pages.get(0).pageNumber()).isEqualTo(1);
            assertThat(pages.get(0).normalizedText()).contains("产品代码：SGN22555");
            assertThat(pages.get(0).normalizedText()).contains("示例理财丙宁欣天天鎏金现金管理类理财产品3号");
            assertThat(pages.get(1).pageNumber()).isEqualTo(2);
            assertThat(pages.get(1).normalizedText()).contains("第二页内容");
            // 页码不丢失
            assertThat(pages.get(0).normalizedText()).doesNotContain("第二页内容");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void throwsOnCorruptedPdf() {
        byte[] garbage = "this is not a pdf file".getBytes();
        assertThatThrownBy(() -> parser.parse(new ByteArrayInputStream(garbage)))
                .isInstanceOf(PdfParseException.class);
    }

    @Test
    void throwsOnEmptyInput() {
        assertThatThrownBy(() -> parser.parse(new ByteArrayInputStream(new byte[0])))
                .isInstanceOf(PdfParseException.class);
    }
}
