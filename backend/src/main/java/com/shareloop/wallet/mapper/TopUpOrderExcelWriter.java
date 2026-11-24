package com.shareloop.wallet.mapper;

import static com.shareloop.wallet.mapper.FinanceReportExcelWriter.displayDate;
import static com.shareloop.wallet.mapper.FinanceReportExcelWriter.row;

import com.shareloop.wallet.dto.AdminTopUpOrderResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

@Component
public class TopUpOrderExcelWriter {
    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int HEADER_ROW = 4;
    private static final int[] COLUMN_WIDTHS = {36, 25, 32, 20, 15, 20, 19, 16, 24, 22, 22, 48};
    private static final XSSFColor ROW_FILL = new XSSFColor(new byte[] {(byte) 243, (byte) 248, (byte) 245}, null);
    private static final XSSFColor ROW_BORDER = new XSSFColor(new byte[] {(byte) 220, (byte) 232, (byte) 225}, null);

    public byte[] write(List<AdminTopUpOrderResponse> orders, LocalDate from, LocalDate to, boolean paidDate) {
        try (var workbook = new XSSFWorkbook();
                var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Giao dịch nạp tiền");
            sheet.setDisplayGridlines(false);
            var title = workbook.createCellStyle();
            var titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 16);
            titleFont.setColor(IndexedColors.DARK_GREEN.getIndex());
            title.setFont(titleFont);
            title.setIndention((short) 1);
            title.setVerticalAlignment(VerticalAlignment.CENTER);
            row(sheet, 0, title, "Giao dịch nạp tiền ShareLoop");
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, COLUMN_WIDTHS.length - 1));
            sheet.getRow(0).setHeightInPoints(32);

            var metadataValue = workbook.createCellStyle();
            metadataValue.setAlignment(HorizontalAlignment.LEFT);
            metadataValue.setVerticalAlignment(VerticalAlignment.CENTER);
            metadataValue.setIndention((short) 1);
            var metadataLabel = workbook.createCellStyle();
            metadataLabel.cloneStyleFrom(metadataValue);
            var metadataFont = workbook.createFont();
            metadataFont.setBold(true);
            metadataFont.setColor(IndexedColors.DARK_GREEN.getIndex());
            metadataLabel.setFont(metadataFont);
            metadataLabel.setFillForegroundColor(ROW_FILL);
            metadataLabel.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            row(
                    sheet,
                    1,
                    metadataValue,
                    "Lọc theo ngày",
                    paidDate ? "Ngày thanh toán" : "Ngày tạo đơn",
                    "Từ ngày",
                    displayDate(from),
                    "Đến ngày",
                    displayDate(to));
            row(sheet, 2, metadataValue, "Số đơn trong tệp", orders.size(), "Múi giờ", "Việt Nam (UTC+7)");
            styleColumns(sheet.getRow(1), metadataLabel, 0, 2, 4);
            styleColumns(sheet.getRow(2), metadataLabel, 0, 2);
            sheet.getRow(1).setHeightInPoints(21);
            sheet.getRow(2).setHeightInPoints(21);
            sheet.createRow(3).setHeightInPoints(8);

            CellStyle header = FinanceReportExcelWriter.tableStyle(workbook, false, IndexedColors.DARK_GREEN);
            var headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            header.setFont(headerFont);
            header.setVerticalAlignment(VerticalAlignment.CENTER);
            header.setIndention((short) 1);
            row(
                    sheet,
                    HEADER_ROW,
                    header,
                    "Mã đơn",
                    "Người nạp",
                    "Email",
                    "Số tiền (VND)",
                    "Số Credit",
                    "Cổng thanh toán",
                    "Trạng thái",
                    "Cần đối soát",
                    "Mã giao dịch VNPay",
                    "Tạo lúc",
                    "Thanh toán lúc",
                    "Ghi chú đối soát");
            var headerRow = sheet.getRow(HEADER_ROW);
            var centeredHeader = workbook.createCellStyle();
            centeredHeader.cloneStyleFrom(header);
            centeredHeader.setAlignment(HorizontalAlignment.CENTER);
            centeredHeader.setIndention((short) 0);
            var numberHeader = workbook.createCellStyle();
            numberHeader.cloneStyleFrom(header);
            numberHeader.setAlignment(HorizontalAlignment.RIGHT);
            numberHeader.setIndention((short) 2);
            styleColumns(headerRow, numberHeader, 3, 4);
            styleColumns(headerRow, centeredHeader, 5, 6, 7, 8, 9, 10);
            headerRow.setHeightInPoints(26);

            var format = workbook.createDataFormat();
            short textFormat = format.getFormat("@");
            short numberFormat = format.getFormat("#,##0");
            short dateFormat = format.getFormat("dd/mm/yyyy hh:mm:ss");
            RowStyles plain = rowStyles(workbook, textFormat, numberFormat, dateFormat, false);
            RowStyles shaded = rowStyles(workbook, textFormat, numberFormat, dateFormat, true);
            for (int index = 0; index < orders.size(); index++) {
                var order = orders.get(index);
                RowStyles styles = index % 2 == 0 ? plain : shaded;
                int rowIndex = HEADER_ROW + 1 + index;
                row(
                        sheet,
                        rowIndex,
                        styles.text(),
                        order.orderCode(),
                        order.userName(),
                        order.userEmail(),
                        order.amountVnd(),
                        order.credits(),
                        FinanceReportCsvWriter.providerLabel(order.provider()),
                        TopUpOrderCsvWriter.statusLabel(order.status()),
                        order.needsReconcile() ? "Có" : "Không",
                        order.providerTxnId(),
                        localTime(order.createdAt()),
                        localTime(order.paidAt()),
                        order.reviewNote());
                var dataRow = sheet.getRow(rowIndex);
                styleColumns(dataRow, styles.wrapped(), 1, 2, 11);
                styleColumns(dataRow, styles.number(), 3, 4);
                styleColumns(dataRow, styles.centered(), 5, 6, 7, 8);
                styleColumns(dataRow, styles.date(), 9, 10);
                long lines = Math.max(
                        wrappedLines(order.reviewNote(), COLUMN_WIDTHS[11]),
                        Math.max(
                                wrappedLines(order.userName(), COLUMN_WIDTHS[1]),
                                wrappedLines(order.userEmail(), COLUMN_WIDTHS[2])));
                dataRow.setHeightInPoints(Math.max(22, Math.min(lines, 25) * 15 + 5));
            }

            for (int column = 0; column < COLUMN_WIDTHS.length; column++) {
                sheet.setColumnWidth(column, COLUMN_WIDTHS[column] * 256);
            }
            sheet.createFreezePane(1, HEADER_ROW + 1);
            sheet.setAutoFilter(
                    new CellRangeAddress(HEADER_ROW, HEADER_ROW + orders.size(), 0, COLUMN_WIDTHS.length - 1));
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write top-up orders workbook", e);
        }
    }

    private static RowStyles rowStyles(
            XSSFWorkbook workbook, short textFormat, short numberFormat, short dateFormat, boolean shaded) {
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

    private static void styleColumns(Row row, CellStyle style, int... columns) {
        for (int column : columns) {
            row.getCell(column).setCellStyle(style);
        }
    }

    private static long wrappedLines(String value, int columnWidth) {
        if (value == null || value.isBlank()) {
            return 1;
        }
        int charactersPerLine = columnWidth - 5;
        return value.lines()
                .mapToLong(line -> Math.max(1, Math.ceilDiv(line.length(), charactersPerLine)))
                .sum();
    }

    private record RowStyles(CellStyle text, CellStyle number, CellStyle centered, CellStyle date, CellStyle wrapped) {}

    private static LocalDateTime localTime(Instant time) {
        return time == null ? null : LocalDateTime.ofInstant(time, VIETNAM_ZONE).withNano(0);
    }
}
