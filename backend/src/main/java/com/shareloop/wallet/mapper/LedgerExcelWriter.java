package com.shareloop.wallet.mapper;

import static com.shareloop.wallet.mapper.ExcelTableSupport.HEADER_ROW;
import static com.shareloop.wallet.mapper.ExcelTableSupport.finishTable;
import static com.shareloop.wallet.mapper.ExcelTableSupport.headerStyles;
import static com.shareloop.wallet.mapper.ExcelTableSupport.localTime;
import static com.shareloop.wallet.mapper.ExcelTableSupport.metadataStyles;
import static com.shareloop.wallet.mapper.ExcelTableSupport.rowStyles;
import static com.shareloop.wallet.mapper.ExcelTableSupport.styleColumns;
import static com.shareloop.wallet.mapper.ExcelTableSupport.wrappedLines;
import static com.shareloop.wallet.mapper.ExcelTableSupport.writeTitle;
import static com.shareloop.wallet.mapper.FinanceReportExcelWriter.displayDate;
import static com.shareloop.wallet.mapper.FinanceReportExcelWriter.row;

import com.shareloop.wallet.dto.AdminLedgerRow;
import com.shareloop.wallet.entity.CreditTxType;
import com.shareloop.wallet.mapper.ExcelTableSupport.RowStyles;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.List;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

/** Xuất file Excel (.xlsx) lịch sử giao dịch Credit toàn hệ thống. */
@Component
public class LedgerExcelWriter {
    private static final int[] COLUMN_WIDTHS = {22, 25, 30, 18, 15, 15, 32, 28, 36, 24};

    public byte[] write(List<AdminLedgerRow> entries, CreditTxType type, LocalDate from, LocalDate to) {
        try (var workbook = new XSSFWorkbook();
                var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Lịch sử giao dịch Credit");
            sheet.setDisplayGridlines(false);
            writeTitle(workbook, sheet, "Lịch sử giao dịch Credit ShareLoop", COLUMN_WIDTHS.length);

            var metadata = metadataStyles(workbook);
            row(
                    sheet,
                    1,
                    metadata.value(),
                    "Loại giao dịch",
                    type == null ? "Tất cả" : LedgerCsvWriter.typeLabel(type),
                    "Từ ngày",
                    displayDate(from),
                    "Đến ngày",
                    displayDate(to));
            row(sheet, 2, metadata.value(), "Số giao dịch trong tệp", entries.size(), "Múi giờ", "Việt Nam (UTC+7)");
            styleColumns(sheet.getRow(1), metadata.label(), 0, 2, 4);
            styleColumns(sheet.getRow(2), metadata.label(), 0, 2);
            sheet.getRow(1).setHeightInPoints(21);
            sheet.getRow(2).setHeightInPoints(21);
            sheet.createRow(3).setHeightInPoints(8);

            var header = headerStyles(workbook);
            row(
                    sheet,
                    HEADER_ROW,
                    header.base(),
                    "Thời gian",
                    "Người dùng",
                    "Email",
                    "Loại",
                    "Số Credit",
                    "Số dư sau",
                    "Bài liên quan",
                    "Mã đơn nạp",
                    "Ghi chú",
                    "Người thực hiện");
            var headerRow = sheet.getRow(HEADER_ROW);
            styleColumns(headerRow, header.centered(), 0, 3, 7);
            styleColumns(headerRow, header.number(), 4, 5);
            headerRow.setHeightInPoints(26);

            RowStyles plain = rowStyles(workbook, false);
            RowStyles shaded = rowStyles(workbook, true);
            for (int index = 0; index < entries.size(); index++) {
                var entry = entries.get(index);
                RowStyles styles = index % 2 == 0 ? plain : shaded;
                int rowIndex = HEADER_ROW + 1 + index;
                row(
                        sheet,
                        rowIndex,
                        styles.text(),
                        localTime(entry.createdAt()),
                        entry.userName(),
                        entry.userEmail(),
                        LedgerCsvWriter.typeLabel(entry.type()),
                        entry.amount(),
                        entry.balanceAfter(),
                        entry.itemTitle(),
                        entry.orderCode(),
                        entry.note(),
                        entry.createdByName());
                var dataRow = sheet.getRow(rowIndex);
                styleColumns(dataRow, styles.date(), 0);
                styleColumns(dataRow, styles.wrapped(), 1, 2, 6, 8, 9);
                styleColumns(dataRow, styles.centered(), 3, 7);
                styleColumns(dataRow, styles.number(), 4, 5);
                long lines = maxLines(
                        wrappedLines(entry.userName(), COLUMN_WIDTHS[1]),
                        wrappedLines(entry.userEmail(), COLUMN_WIDTHS[2]),
                        wrappedLines(entry.itemTitle(), COLUMN_WIDTHS[6]),
                        wrappedLines(entry.note(), COLUMN_WIDTHS[8]),
                        wrappedLines(entry.createdByName(), COLUMN_WIDTHS[9]));
                dataRow.setHeightInPoints(Math.max(22, Math.min(lines, 25) * 15 + 5));
            }

            finishTable(sheet, COLUMN_WIDTHS, entries.size());
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write credit ledger workbook", e);
        }
    }

    private static long maxLines(long... counts) {
        long max = 1;
        for (long count : counts) {
            if (count > max) {
                max = count;
            }
        }
        return max;
    }
}
