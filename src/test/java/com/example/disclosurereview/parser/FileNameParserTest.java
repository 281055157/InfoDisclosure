package com.example.disclosurereview.parser;

import com.example.disclosurereview.model.FileNameInfo;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FileNameParserTest {

    private final FileNameParser parser = new FileNameParser();

    @Test
    void parsesStandardFileName() {
        FileNameInfo info = parser.parse("SGN22555_投资协议书.pdf");
        assertThat(info.originalFileName()).isEqualTo("SGN22555_投资协议书.pdf");
        assertThat(info.productCode()).isEqualTo("SGN22555");
        assertThat(info.declaredDocumentType()).isEqualTo("投资协议书");
    }

    @Test
    void handlesMultipleUnderscoresInType() {
        FileNameInfo info = parser.parse("SGN22555_发行公告_补充说明.pdf");
        assertThat(info.productCode()).isEqualTo("SGN22555");
        assertThat(info.declaredDocumentType()).isEqualTo("发行公告_补充说明");
    }

    @Test
    void stripsBracketsAndWhitespace() {
        FileNameInfo info = parser.parse("  SGN22555_投资协议书（盖章版）.pdf ");
        assertThat(info.productCode()).isEqualTo("SGN22555");
        assertThat(info.declaredDocumentType()).isEqualTo("投资协议书");
    }

    @Test
    void handlesNoUnderscore() {
        FileNameInfo info = parser.parse("SGN22555.pdf");
        assertThat(info.productCode()).isEqualTo("SGN22555");
        assertThat(info.declaredDocumentType()).isNull();
    }

    @Test
    void handlesEnglishBrackets() {
        FileNameInfo info = parser.parse("SGN22556_产品说明书(最终版).pdf");
        assertThat(info.productCode()).isEqualTo("SGN22556");
        assertThat(info.declaredDocumentType()).isEqualTo("产品说明书");
    }

    @Test
    void handlesBlankName() {
        FileNameInfo info = parser.parse(null);
        assertThat(info.productCode()).isNull();
        assertThat(info.declaredDocumentType()).isNull();
    }
}
