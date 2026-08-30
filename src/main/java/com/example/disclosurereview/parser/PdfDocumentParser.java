package com.example.disclosurereview.parser;

import com.example.disclosurereview.exception.PdfEncryptedException;
import com.example.disclosurereview.exception.PdfParseException;
import com.example.disclosurereview.model.DocumentPage;
import com.example.disclosurereview.util.TextNormalizer;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 使用 PDFBox 逐页提取 PDF 文本，页码从 1 开始。
 * 当前仅支持电子 PDF，不接入 OCR。
 */
@Component
public class PdfDocumentParser {

    /**
     * 逐页解析 PDF。
     *
     * @throws PdfEncryptedException PDF 已加密
     * @throws PdfParseException     PDF 损坏或无法解析
     */
    public List<DocumentPage> parse(InputStream inputStream) {
        byte[] bytes;
        try {
            bytes = inputStream.readAllBytes();
        } catch (IOException e) {
            throw new PdfParseException("读取PDF输入流失败", e);
        }
        if (bytes.length == 0) {
            throw new PdfParseException("PDF文件为空");
        }
        try (PDDocument document = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            int pageCount = document.getNumberOfPages();
            List<DocumentPage> pages = new ArrayList<>(pageCount);
            for (int i = 1; i <= pageCount; i++) {
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                String raw = stripper.getText(document);
                pages.add(new DocumentPage(i, raw, TextNormalizer.normalizePage(raw)));
            }
            return pages;
        } catch (InvalidPasswordException e) {
            throw new PdfEncryptedException("PDF文件已加密，无法解析", e);
        } catch (IOException e) {
            throw new PdfParseException("PDF解析失败: " + e.getMessage(), e);
        }
    }
}
