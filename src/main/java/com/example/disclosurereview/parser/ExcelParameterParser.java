package com.example.disclosurereview.parser;

import com.example.disclosurereview.config.ReviewProperties;
import com.example.disclosurereview.exception.ExcelParseException;
import com.example.disclosurereview.model.ExcelParameterResult;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.InputStream;

/**
 * 使用 Apache POI 读取 Excel 参数表。
 * 默认读取第一个工作表的 B9 单元格，可通过配置指定工作表名称。
 */
@Component
public class ExcelParameterParser {

    private static final String CELL_ADDRESS = "B9";
    private static final int ROW_INDEX = 8; // 第9行，0基
    private static final int COL_INDEX = 1; // B列，0基

    private final ReviewProperties properties;

    public ExcelParameterParser(ReviewProperties properties) {
        this.properties = properties;
    }

    /**
     * 读取 B9 单元格。
     *
     * @return B9 读取结果；单元格为空时 normalizedValue 为 null
     * @throws ExcelParseException Excel 损坏、配置的工作表不存在等
     */
    public ExcelParameterResult parseB9(InputStream inputStream) {
        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = resolveSheet(workbook);
            Row row = sheet.getRow(ROW_INDEX);
            Cell cell = row == null ? null : row.getCell(COL_INDEX);
            String raw = null;
            if (cell != null) {
                DataFormatter formatter = new DataFormatter();
                raw = formatter.formatCellValue(cell);
            }
            String normalized = StringUtils.hasText(raw) ? raw.strip() : null;
            if (normalized != null && normalized.isEmpty()) {
                normalized = null;
            }
            return new ExcelParameterResult(sheet.getSheetName(), CELL_ADDRESS, raw, normalized);
        } catch (ExcelParseException e) {
            throw e;
        } catch (Exception e) {
            throw new ExcelParseException("Excel解析失败: " + e.getMessage(), e);
        }
    }

    private Sheet resolveSheet(Workbook workbook) {
        String configured = properties.getExcel().getB9SheetName();
        if (StringUtils.hasText(configured)) {
            Sheet sheet = workbook.getSheet(configured);
            if (sheet == null) {
                throw new ExcelParseException("配置的工作表不存在: " + configured);
            }
            return sheet;
        }
        if (workbook.getNumberOfSheets() == 0) {
            throw new ExcelParseException("Excel中不存在任何工作表");
        }
        return workbook.getSheetAt(0);
    }
}
