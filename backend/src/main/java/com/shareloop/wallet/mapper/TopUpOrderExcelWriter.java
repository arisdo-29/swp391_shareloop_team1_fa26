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

import com.shareloop.wallet.dto.AdminTopUpOrderResponse;
import com.shareloop.wallet.mapper.ExcelTableSupport.RowStyles;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.List;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

@Component
public class TopUpOrderExcelWriter {
    private static final int[] COLUMN_WIDTHS = {36, 25, 32, 20, 15, 20, 19, 24, 22, 22};

    public byte[] write(List<AdminTopUpOrderResponse> orders, LocalDate from, LocalDate to, boolean paidDate) {
        try (var workbook = new XSSFWorkbook();
                var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Giao dịch nạp tiền");
            sheet.setDisplayGridlines(false);
            writeTitle(workbook, sheet, "Giao dịch nạp tiền ShareLoop", COLUMN_WIDTHS.length);

            var metadata = metadataStyles(workbook);
            row(
                    sheet,
                    1,
                    metadata.value(),
                    "Lọc theo ngày",
                    paidDate ? "Ngày thanh toán" : "Ngày tạo đơn",
                    "Từ ngày",
                    displayDate(from),
                    "Đến ngày",
                    displayDate(to));
            row(sheet, 2, metadata.value(), "Số đơn trong tệp", orders.size(), "Múi giờ", "Việt Nam (UTC+7)");
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
                    "Mã đơn",
                    "Người nạp",
                    "Email",
                    "Số tiền (VND)",
                    "Số Credit",
                    "Cổng thanh toán",
                    "Trạng thái",
                    "Mã giao dịch VNPay",
                    "Tạo lúc",
                    "Thanh toán lúc");
            var headerRow = sheet.getRow(HEADER_ROW);
            styleColumns(headerRow, header.number(), 3, 4);
            styleColumns(headerRow, header.centered(), 5, 6, 7, 8, 9);
            headerRow.setHeightInPoints(26);

            RowStyles plain = rowStyles(workbook, false);
            RowStyles shaded = rowStyles(workbook, true);
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
                        order.providerTxnId(),
                        localTime(order.createdAt()),
                        localTime(order.paidAt()));
                var dataRow = sheet.getRow(rowIndex);
                styleColumns(dataRow, styles.wrapped(), 1, 2);
                styleColumns(dataRow, styles.number(), 3, 4);
                styleColumns(dataRow, styles.centered(), 5, 6, 7);
                styleColumns(dataRow, styles.date(), 8, 9);
                long lines = Math.max(
                        wrappedLines(order.userName(), COLUMN_WIDTHS[1]),
                        wrappedLines(order.userEmail(), COLUMN_WIDTHS[2]));
                dataRow.setHeightInPoints(Math.max(22, Math.min(lines, 25) * 15 + 5));
            }

            finishTable(sheet, COLUMN_WIDTHS, orders.size());
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write top-up orders workbook", e);
        }
    }
}
