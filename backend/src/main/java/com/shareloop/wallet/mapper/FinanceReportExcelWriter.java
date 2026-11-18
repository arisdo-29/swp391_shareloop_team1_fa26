package com.shareloop.wallet.mapper;

import com.shareloop.wallet.dto.CreditTypeTotal;
import com.shareloop.wallet.dto.DailyCashTotal;
import com.shareloop.wallet.dto.FinanceSummaryResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

@Component
public class FinanceReportExcelWriter {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter EXPORTED_AT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private final Clock clock;

    public FinanceReportExcelWriter(Clock clock) {
        this.clock = clock;
    }

    public byte[] write(FinanceSummaryResponse report, LocalDate from, LocalDate to) {
        try (var workbook = new XSSFWorkbook();
                var output = new ByteArrayOutputStream()) {
            CellStyle title = workbook.createCellStyle();
            var titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            title.setFont(titleFont);

            CellStyle header = tableStyle(workbook, true, IndexedColors.PALE_BLUE);
            CellStyle number = tableStyle(workbook, false, null);
            number.setDataFormat(workbook.createDataFormat().getFormat("#,##0"));
            CellStyle totals = tableStyle(workbook, true, null);
            totals.setDataFormat(number.getDataFormat());
            CellStyle date = tableStyle(workbook, false, null);
            date.setDataFormat(workbook.createDataFormat().getFormat("dd/mm/yyyy"));

            Sheet summary = workbook.createSheet("Tổng hợp");
            row(summary, 0, title, "Báo cáo tài chính ShareLoop");
            summary.addMergedRegion(new CellRangeAddress(0, 0, 0, 3));
            row(summary, 1, null, "Từ ngày", displayDate(from), "Đến ngày", displayDate(to));
            row(
                    summary,
                    2,
                    null,
                    "Thời điểm xuất",
                    EXPORTED_AT.format(clock.instant().atZone(VIETNAM_ZONE)));
            row(summary, 4, title, "Tiền mặt thu");
            row(summary, 5, header, "Số đơn nạp thành công", "Số tiền thu (VND)");
            row(summary, 6, totals, report.cashOrderCount(), report.cashAmountVnd());
            int next = creditSection(
                    summary,
                    8,
                    title,
                    header,
                    number,
                    totals,
                    "Credit cộng vào",
                    report.creditInByType(),
                    report.creditInTotal());
            next = creditSection(
                    summary,
                    next,
                    title,
                    header,
                    number,
                    totals,
                    "Credit trừ ra",
                    report.creditOutByType(),
                    report.creditOutTotal());
            var reconciliation = report.reconciliation();
            row(summary, next++, title, "Đối chiếu nạp Credit");
            row(summary, next++, header, "Credit quy đổi từ tiền thu", "Credit nạp ghi sổ", "Chênh lệch", "Kết quả");
            row(
                    summary,
                    next,
                    number,
                    reconciliation.cashCredits(),
                    reconciliation.topUpCredits(),
                    reconciliation.difference(),
                    reconciliation.matched() ? "Khớp" : "Lệch");
            summary.getRow(next)
                    .getCell(3)
                    .setCellStyle(tableStyle(
                            workbook, true, reconciliation.matched() ? IndexedColors.LIGHT_GREEN : IndexedColors.ROSE));
            finish(summary, 4, 6);

            Sheet daily = workbook.createSheet("Tiền thu theo ngày");
            row(daily, 0, header, "Ngày", "Tiền thu (VND)", "Số đơn nạp thành công");
            int index = 1;
            for (DailyCashTotal day : report.cashByDay()) {
                row(daily, index, number, day.date(), day.amountVnd(), day.orderCount());
                daily.getRow(index++).getCell(0).setCellStyle(date);
            }
            row(daily, index, totals, "Tổng", report.cashAmountVnd(), report.cashOrderCount());
            finish(daily, 3, 1);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write financial report workbook", e);
        }
    }

    public String fileName(LocalDate from, LocalDate to) {
        return "bao-cao-tai-chinh_" + (from == null ? "bat-dau" : from.format(FILE_DATE)) + "_"
                + (to == null ? "hien-tai" : to.format(FILE_DATE)) + ".xlsx";
    }

    private static int creditSection(
            Sheet sheet,
            int index,
            CellStyle title,
            CellStyle header,
            CellStyle number,
            CellStyle totals,
            String name,
            List<CreditTypeTotal> entries,
            long total) {
        row(sheet, index++, title, name);
        row(sheet, index++, header, "Loại giao dịch", "Số Credit", "Số lượt");
        for (CreditTypeTotal entry : entries) {
            row(
                    sheet,
                    index++,
                    number,
                    FinanceReportCsvWriter.typeLabel(entry.type()),
                    entry.amount(),
                    entry.entryCount());
        }
        row(
                sheet,
                index,
                totals,
                "Tổng",
                total,
                entries.stream().mapToLong(CreditTypeTotal::entryCount).sum());
        return index + 2;
    }

    private static CellStyle tableStyle(XSSFWorkbook workbook, boolean bold, IndexedColors fill) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        if (bold) {
            var font = workbook.createFont();
            font.setBold(true);
            style.setFont(font);
        }
        if (fill != null) {
            style.setFillForegroundColor(fill.getIndex());
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
        return style;
    }

    private static void row(Sheet sheet, int index, CellStyle style, Object... values) {
        var row = sheet.createRow(index);
        for (int column = 0; column < values.length; column++) {
            var cell = row.createCell(column);
            switch (values[column]) {
                case Number value -> cell.setCellValue(value.doubleValue());
                case LocalDate value -> cell.setCellValue(value);
                default -> cell.setCellValue(values[column].toString());
            }
            if (style != null) cell.setCellStyle(style);
        }
    }

    private static void finish(Sheet sheet, int columnCount, int headerRows) {
        // Tính theo nội dung hiển thị để chạy được trên cả container headless không có font hệ thống.
        DataFormatter formatter = new DataFormatter(Locale.forLanguageTag("vi-VN"));
        int[] widths = new int[columnCount];
        for (var row : sheet) {
            for (var cell : row) {
                int column = cell.getColumnIndex();
                widths[column] =
                        Math.max(widths[column], formatter.formatCellValue(cell).length());
            }
        }
        for (int column = 0; column < columnCount; column++) {
            sheet.setColumnWidth(column, Math.min(255, widths[column] + 4) * 256);
        }
        sheet.createFreezePane(0, headerRows);
    }

    private static String displayDate(LocalDate date) {
        return date == null ? "Không giới hạn" : date.format(DISPLAY_DATE);
    }
}
