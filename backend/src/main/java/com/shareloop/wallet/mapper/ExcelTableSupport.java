package com.shareloop.wallet.mapper;

import static com.shareloop.wallet.mapper.FinanceReportExcelWriter.row;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** Kiểu ô và bố cục dùng chung cho các tệp Excel bảng dữ liệu tài chính. */
final class ExcelTableSupport {
    static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    static final int HEADER_ROW = 4;
    static final XSSFColor ROW_FILL = new XSSFColor(new byte[] {(byte) 243, (byte) 248, (byte) 245}, null);
    static final XSSFColor ROW_BORDER = new XSSFColor(new byte[] {(byte) 220, (byte) 232, (byte) 225}, null);

    private ExcelTableSupport() {}

    static void writeTitle(XSSFWorkbook workbook, Sheet sheet, String titleText, int columnCount) {
        var title = workbook.createCellStyle();
        var titleFont = workbook.createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 16);
        titleFont.setColor(IndexedColors.DARK_GREEN.getIndex());
        title.setFont(titleFont);
        title.setIndention((short) 1);
        title.setVerticalAlignment(VerticalAlignment.CENTER);
        row(sheet, 0, title, titleText);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, columnCount - 1));
        sheet.getRow(0).setHeightInPoints(32);
    }

    static MetadataStyles metadataStyles(XSSFWorkbook workbook) {
        var value = workbook.createCellStyle();
        value.setAlignment(HorizontalAlignment.LEFT);
        value.setVerticalAlignment(VerticalAlignment.CENTER);
        value.setIndention((short) 1);
        var label = workbook.createCellStyle();
        label.cloneStyleFrom(value);
        var font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.DARK_GREEN.getIndex());
        label.setFont(font);
        label.setFillForegroundColor(ROW_FILL);
        label.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return new MetadataStyles(label, value);
    }

    static HeaderStyles headerStyles(XSSFWorkbook workbook) {
        CellStyle base = FinanceReportExcelWriter.tableStyle(workbook, false, IndexedColors.DARK_GREEN);
        var headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerFont.setColor(IndexedColors.WHITE.getIndex());
        base.setFont(headerFont);
        base.setVerticalAlignment(VerticalAlignment.CENTER);
        base.setIndention((short) 1);

        var centered = workbook.createCellStyle();
        centered.cloneStyleFrom(base);
        centered.setAlignment(HorizontalAlignment.CENTER);
        centered.setIndention((short) 0);

        var number = workbook.createCellStyle();
        number.cloneStyleFrom(base);
        number.setAlignment(HorizontalAlignment.RIGHT);
        number.setIndention((short) 2);
        return new HeaderStyles(base, centered, number);
    }

    static RowStyles rowStyles(XSSFWorkbook workbook, boolean shaded) {
        var format = workbook.createDataFormat();
        short textFormat = format.getFormat("@");
        short numberFormat = format.getFormat("#,##0");
        short dateFormat = format.getFormat("dd/mm/yyyy hh:mm:ss");

        var text = workbook.createCellStyle();
        text.setDataFormat(textFormat);
        text.setVerticalAlignment(VerticalAlignment.CENTER);
        text.setIndention((short) 1);
        text.setBorderBottom(BorderStyle.HAIR);
        text.setBottomBorderColor(ROW_BORDER);
        if (shaded) {
            text.setFillForegroundColor(ROW_FILL);
            text.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
        CellStyle number = workbook.createCellStyle();
        number.cloneStyleFrom(text);
        number.setDataFormat(numberFormat);
        number.setAlignment(HorizontalAlignment.RIGHT);

        CellStyle centered = workbook.createCellStyle();
        centered.cloneStyleFrom(text);
        centered.setAlignment(HorizontalAlignment.CENTER);
        centered.setIndention((short) 0);

        CellStyle date = workbook.createCellStyle();
        date.cloneStyleFrom(centered);
        date.setDataFormat(dateFormat);

        CellStyle wrapped = workbook.createCellStyle();
        wrapped.cloneStyleFrom(text);
        wrapped.setWrapText(true);
        return new RowStyles(text, number, centered, date, wrapped);
    }

    static void styleColumns(Row row, CellStyle style, int... columns) {
        for (int column : columns) {
            row.getCell(column).setCellStyle(style);
        }
    }

    static long wrappedLines(String value, int columnWidth) {
        if (value == null || value.isBlank()) {
            return 1;
        }
        int charactersPerLine = Math.max(1, columnWidth - 5);
        return value.lines()
                .mapToLong(line -> Math.max(1, Math.ceilDiv(line.length(), charactersPerLine)))
                .sum();
    }

    static void finishTable(XSSFSheet sheet, int[] columnWidths, int rowCount) {
        for (int column = 0; column < columnWidths.length; column++) {
            sheet.setColumnWidth(column, columnWidths[column] * 256);
        }
        sheet.createFreezePane(1, HEADER_ROW + 1);
        sheet.setAutoFilter(new CellRangeAddress(HEADER_ROW, HEADER_ROW + rowCount, 0, columnWidths.length - 1));
    }

    static LocalDateTime localTime(Instant time) {
        return time == null ? null : LocalDateTime.ofInstant(time, VIETNAM_ZONE).withNano(0);
    }

    record MetadataStyles(CellStyle label, CellStyle value) {}

    record HeaderStyles(CellStyle base, CellStyle centered, CellStyle number) {}

    record RowStyles(CellStyle text, CellStyle number, CellStyle centered, CellStyle date, CellStyle wrapped) {}
}
