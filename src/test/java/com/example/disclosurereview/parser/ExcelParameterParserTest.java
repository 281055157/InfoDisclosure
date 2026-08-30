package com.example.disclosurereview.parser;

import com.example.disclosurereview.config.ReviewProperties;
import com.example.disclosurereview.exception.ExcelParseException;
import com.example.disclosurereview.model.ExcelParameterResult;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExcelParameterParserTest {

    private ExcelParameterParser parser(String sheetName) {
        ReviewProperties props = new ReviewProperties();
        props.getExcel().setB9SheetName(sheetName);
        return new ExcelParameterParser(props);
    }

    private byte[] excelWithB9(String sheetName, String b9Value) throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet(sheetName);
            Row row = sheet.createRow(8); // 第9行
            row.createCell(1).setCellValue(b9Value); // B列
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    @Test
    void readsB9FromFirstSheet() throws Exception {
        byte[] bytes = excelWithB9("参数表", "成立公告");
        ExcelParameterResult result = parser(null).parseB9(new ByteArrayInputStream(bytes));
        assertThat(result.sheetName()).isEqualTo("参数表");
        assertThat(result.cellAddress()).isEqualTo("B9");
        assertThat(result.normalizedValue()).isEqualTo("成立公告");
    }

    @Test
    void readsB9FromConfiguredSheet() throws Exception {
        byte[] bytes = excelWithB9("公告参数", "到期公告");
        ExcelParameterResult result = parser("公告参数").parseB9(new ByteArrayInputStream(bytes));
        assertThat(result.sheetName()).isEqualTo("公告参数");
        assertThat(result.normalizedValue()).isEqualTo("到期公告");
    }

    @Test
    void returnsNullWhenB9Empty() throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            wb.createSheet("空表");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            ExcelParameterResult result = parser(null).parseB9(new ByteArrayInputStream(out.toByteArray()));
            assertThat(result.normalizedValue()).isNull();
        }
    }

    @Test
    void throwsWhenConfiguredSheetMissing() throws Exception {
        byte[] bytes = excelWithB9("参数表", "成立公告");
        assertThatThrownBy(() -> parser("不存在").parseB9(new ByteArrayInputStream(bytes)))
                .isInstanceOf(ExcelParseException.class);
    }

    @Test
    void throwsOnCorruptedExcel() {
        assertThatThrownBy(() -> parser(null).parseB9(new ByteArrayInputStream("not excel".getBytes())))
                .isInstanceOf(ExcelParseException.class);
    }
}
