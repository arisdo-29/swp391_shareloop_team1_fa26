package com.shareloop.wallet.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.shareloop.wallet.dto.AdminLedgerRow;
import com.shareloop.wallet.entity.CreditTxType;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class LedgerExcelWriterTest {
    private final LedgerExcelWriter writer = new LedgerExcelWriter();

    @Test
    void write_writesMetadataHeadersAndTypedRowsInVietnamTime() throws Exception {
        var topUp = new AdminLedgerRow(
                10L,
                Instant.parse("2026-11-27T17:15:00Z"),
                1L,
                "Lan Sổ Cái",
                "lan@shareloop.test",
                CreditTxType.TOP_UP,
                50,
                150L,
                null,
                null,
                "0000123",
                null,
                null);
        var adjust = new AdminLedgerRow(
                9L,
                Instant.parse("2026-11-26T03:00:00Z"),
                2L,
                "Bình Sổ Cái",
                "binh@shareloop.test",
                CreditTxType.ADMIN_ADJUST,
                -5,
                15L,
                100L,
                "Bàn phím cơ",
                null,
                "Trừ nhầm lần trước",
                "Quản Trị Sổ Cái");

        byte[] bytes = writer.write(
                List.of(topUp, adjust), CreditTxType.TOP_UP, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 28));

        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var sheet = workbook.getSheet("Lịch sử giao dịch Credit");
            assertThat(sheet).isNotNull();
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Lịch sử giao dịch Credit ShareLoop");
            assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("Nạp Credit");
            assertThat(sheet.getRow(1).getCell(3).getStringCellValue()).isEqualTo("01/11/2026");
            assertThat(sheet.getRow(1).getCell(5).getStringCellValue()).isEqualTo("28/11/2026");
            assertThat(sheet.getRow(2).getCell(1).getNumericCellValue()).isEqualTo(2.0);
            assertThat(sheet.getPaneInformation().isFreezePane()).isTrue();
            assertThat(sheet.getPaneInformation().getVerticalSplitPosition()).isEqualTo((short) 1);
            assertThat(sheet.getPaneInformation().getHorizontalSplitPosition()).isEqualTo((short) 5);
            assertThat(sheet.getCTWorksheet().getAutoFilter().getRef()).isEqualTo("A5:J7");

            // 2026-11-27T17:15:00Z = 2026-11-28 00:15:00 UTC+7
            var first = sheet.getRow(5);
            assertThat(first.getCell(0).getLocalDateTimeCellValue()).isEqualTo(LocalDateTime.of(2026, 11, 28, 0, 15));
            assertThat(first.getCell(3).getStringCellValue()).isEqualTo("Nạp Credit");
            assertThat(first.getCell(3).getCellStyle().getAlignment()).isEqualTo(HorizontalAlignment.CENTER);
            assertThat(first.getCell(4).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(first.getCell(4).getNumericCellValue()).isEqualTo(50.0);
            assertThat(first.getCell(4).getCellStyle().getAlignment()).isEqualTo(HorizontalAlignment.RIGHT);
            assertThat(first.getCell(5).getNumericCellValue()).isEqualTo(150.0);
            assertThat(first.getCell(7).getStringCellValue()).isEqualTo("0000123");
            assertThat(first.getCell(7).getCellStyle().getDataFormatString()).isEqualTo("@");
            assertThat(first.getCell(0).getCellStyle().getVerticalAlignment()).isEqualTo(VerticalAlignment.CENTER);

            var second = sheet.getRow(6);
            assertThat(second.getCell(1).getCellStyle().getWrapText()).isTrue();
            assertThat(second.getCell(3).getStringCellValue()).isEqualTo("Admin điều chỉnh");
            assertThat(second.getCell(4).getNumericCellValue()).isEqualTo(-5.0);
            assertThat(second.getCell(6).getStringCellValue()).isEqualTo("Bàn phím cơ");
            assertThat(second.getCell(8).getStringCellValue()).isEqualTo("Trừ nhầm lần trước");
            assertThat(second.getCell(9).getStringCellValue()).isEqualTo("Quản Trị Sổ Cái");
        }
    }
}
