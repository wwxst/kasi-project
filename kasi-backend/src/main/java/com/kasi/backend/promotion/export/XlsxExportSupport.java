package com.kasi.backend.promotion.export;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

public final class XlsxExportSupport {
    private static final ZoneId EXPORT_ZONE = ZoneId.of("Asia/Shanghai");

    private XlsxExportSupport() {
    }

    @SuppressWarnings("deprecation")
    public static <T> byte[] write(String sheetName, List<String> headers,
                                   List<T> values, RowWriter<T> rowWriter) {
        SXSSFWorkbook workbook = new SXSSFWorkbook(100);
        try (workbook; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet(sheetName);
            Row header = sheet.createRow(0);
            for (int index = 0; index < headers.size(); index++) {
                text(header, index, headers.get(index));
            }
            CellStyle dateStyle = dateStyle(workbook);
            for (int index = 0; index < values.size(); index++) {
                rowWriter.write(sheet.createRow(index + 1), values.get(index), dateStyle);
            }
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("XLSX 导出失败", exception);
        } finally {
            workbook.dispose();
        }
    }

    public static void text(Row row, int column, Object value) {
        Cell cell = row.createCell(column, org.apache.poi.ss.usermodel.CellType.STRING);
        cell.setCellValue(value == null ? "" : value.toString());
    }

    public static void number(Row row, int column, BigDecimal value) {
        Cell cell = row.createCell(column, org.apache.poi.ss.usermodel.CellType.NUMERIC);
        if (value != null) {
            cell.setCellValue(value.doubleValue());
        }
    }

    public static void date(Row row, int column, LocalDateTime value, CellStyle dateStyle) {
        Cell cell = row.createCell(column, org.apache.poi.ss.usermodel.CellType.NUMERIC);
        if (value != null) {
            cell.setCellValue(Date.from(value.atZone(EXPORT_ZONE).toInstant()));
            cell.setCellStyle(dateStyle);
        }
    }

    private static CellStyle dateStyle(SXSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        DataFormat dataFormat = workbook.createDataFormat();
        style.setDataFormat(dataFormat.getFormat("yyyy-mm-dd hh:mm:ss"));
        return style;
    }

    @FunctionalInterface
    public interface RowWriter<T> {
        void write(Row row, T value, CellStyle dateStyle);
    }
}
