package com.example.disclosurereview.llm;

import com.example.disclosurereview.model.DocumentPage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentChunkerTest {

    private final DocumentChunker chunker = new DocumentChunker();

    @Test
    void singleChunkWhenSmall() {
        List<DocumentPage> pages = List.of(
                new DocumentPage(1, "", "第一页"),
                new DocumentPage(2, "", "第二页"));
        List<DocumentChunker.TextChunk> chunks = chunker.chunk(pages, 30000, 12000);
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).text()).contains("[PAGE 1]").contains("[PAGE 2]");
        assertThat(chunks.get(0).fromPage()).isEqualTo(1);
        assertThat(chunks.get(0).toPage()).isEqualTo(2);
    }

    @Test
    void splitsWithOverlapWhenLarge() {
        // 构造 10 页，每页约 2000 字符，chunkChars=5000 -> 每块2页左右
        List<DocumentPage> pages = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            pages.add(new DocumentPage(i, "", "第" + i + "页 " + "长".repeat(2000)));
        }
        List<DocumentChunker.TextChunk> chunks = chunker.chunk(pages, 8000, 5000);
        assertThat(chunks.size()).isGreaterThan(1);
        // 相邻块重叠一页
        for (int i = 1; i < chunks.size(); i++) {
            assertThat(chunks.get(i).fromPage()).isEqualTo(chunks.get(i - 1).toPage());
        }
        // 每块都有页码标记
        for (DocumentChunker.TextChunk c : chunks) {
            assertThat(c.text()).contains("[PAGE " + c.fromPage() + "]");
            assertThat(c.text()).contains("[PAGE " + c.toPage() + "]");
        }
        // 覆盖所有页
        assertThat(chunks.get(chunks.size() - 1).toPage()).isEqualTo(10);
    }
}
