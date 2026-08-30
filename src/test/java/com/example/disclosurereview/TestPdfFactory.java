package com.example.disclosurereview;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 测试工具：动态生成包含中文文本的多页 PDF。
 * 使用 classpath 下的中文字体（simhei.ttf）。
 */
public final class TestPdfFactory {

    private TestPdfFactory() {
    }

    /**
     * 生成 PDF，每个元素一页。
     */
    public static InputStream pdfWithPages(String... pageTexts) {
        try (PDDocument doc = new PDDocument()) {
            PDType0Font font;
            try (InputStream fontStream = TestPdfFactory.class.getResourceAsStream("/fonts/simhei.ttf")) {
                if (fontStream == null) {
                    throw new IllegalStateException("测试字体 fonts/simhei.ttf 不存在");
                }
                font = PDType0Font.load(doc, fontStream);
            }
            for (String text : pageTexts) {
                PDPage page = new PDPage();
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.beginText();
                    cs.setFont(font, 12);
                    cs.newLineAtOffset(50, 700);
                    String[] lines = text.split("\n");
                    for (String line : lines) {
                        cs.showText(line);
                        cs.newLineAtOffset(0, -20);
                    }
                    cs.endText();
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return new ByteArrayInputStream(out.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("生成测试PDF失败", e);
        }
    }
}
