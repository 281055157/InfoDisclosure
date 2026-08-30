package com.example.disclosurereview.llm;

import com.example.disclosurereview.model.DocumentPage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 按页累加切分文本块：
 * - 全文不超过 maxInputChars 时单块一次调用；
 * - 每块目标约 chunkChars 字符；
 * - 相邻文本块重叠最后一页；
 * - 每块带 [PAGE n] 页码标记。
 */
@Component
public class DocumentChunker {

    public List<TextChunk> chunk(List<DocumentPage> pages, int maxInputChars, int chunkChars) {
        int total = pages.stream().mapToInt(p -> p.normalizedText().length()).sum();
        List<TextChunk> chunks = new ArrayList<>();
        if (total <= maxInputChars || pages.size() <= 1) {
            chunks.add(buildChunk(pages, 0, pages.size() - 1));
            return chunks;
        }
        int start = 0;
        while (start < pages.size()) {
            int endExclusive = start;
            int size = 0;
            while (endExclusive < pages.size()) {
                int pageLen = pages.get(endExclusive).normalizedText().length() + 16;
                if (endExclusive > start && size + pageLen > chunkChars) {
                    break;
                }
                size += pageLen;
                endExclusive++;
            }
            int end = endExclusive - 1; // 最后一个包含页的下标
            chunks.add(buildChunk(pages, start, end));
            if (endExclusive >= pages.size()) {
                break;
            }
            // 重叠最后一页
            start = end;
        }
        return chunks;
    }

    private TextChunk buildChunk(List<DocumentPage> pages, int from, int to) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i <= to; i++) {
            DocumentPage p = pages.get(i);
            sb.append("[PAGE ").append(p.pageNumber()).append("]\n");
            sb.append(p.normalizedText()).append("\n\n");
        }
        return new TextChunk(sb.toString(), pages.get(from).pageNumber(), pages.get(to).pageNumber());
    }

    /**
     * 一个文本块，含页码标记的正文。
     */
    public record TextChunk(String text, int fromPage, int toPage) {
    }
}
