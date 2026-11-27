package com.shareloop.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shareloop.common.exception.BusinessException;
import com.shareloop.support.IntegrationTest;
import com.shareloop.wallet.dto.AdminLedgerPage;
import com.shareloop.wallet.dto.AdminLedgerRow;
import com.shareloop.wallet.entity.CreditTxType;
import com.shareloop.wallet.service.AdminLedgerService;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Lịch sử giao dịch Credit toàn hệ thống: lọc, phân trang, tổng vào và ra, CSV. Dùng năm 2003 để tách khỏi dữ liệu khác. */
class AdminLedgerIT extends IntegrationTest {

    private static final LocalDate MARCH_FIRST = LocalDate.of(2003, 3, 1);
    private static final LocalDate MARCH_LAST = LocalDate.of(2003, 3, 31);

    @Autowired
    AdminLedgerService adminLedgerService;

    @Autowired
    JdbcTemplate jdbc;

    long userA;
    long userB;
    long adminId;
    String keyA;
    long orderId;
    String orderCode;

    @BeforeEach
    void setUp() {
        keyA = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        userA = insertUser("ledger-a-" + keyA + "@test.local", "Lan Sổ Cái", "09" + keyA.substring(0, 8));
        userB = insertUser("ledger-b-" + keyA + "@test.local", "Bình Sổ Cái", "08" + keyA.substring(0, 8));
        adminId = insertUser("ledger-admin-" + keyA + "@test.local", "Quản Trị Sổ Cái", "07" + keyA.substring(0, 8));
        orderCode = "LEDGER" + keyA;
        orderId = jdbc.queryForObject(
                "INSERT INTO payment_orders (user_id, order_code, amount_vnd, credits, vnd_per_credit, provider, status)"
                        + " VALUES (?, ?, 50000, 50, 1000, 'VNPAY', 'SUCCESS') RETURNING id",
                Long.class,
                userA,
                orderCode);
        // A nạp 50 (4/3), A trả phí đăng 10 (5/3), B bị Admin điều chỉnh -5 (6/3), A nạp 20 ngoài tháng 3 (5/4).
        insertLedger(userA, 50, "TOP_UP", 50, orderId, null, null, "2003-03-04T03:00:00Z");
        insertLedger(userA, -10, "POST_FEE", 40, null, null, userA, "2003-03-05T03:00:00Z");
        insertLedger(userB, -5, "ADMIN_ADJUST", 15, null, "Trừ nhầm lần trước", adminId, "2003-03-06T03:00:00Z");
        insertLedger(userA, 20, "TOP_UP", 60, null, null, null, "2003-04-05T03:00:00Z");
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM credit_ledger WHERE user_id IN (?, ?)", userA, userB);
        jdbc.update("DELETE FROM payment_orders WHERE id = ?", orderId);
        jdbc.update("DELETE FROM users WHERE id IN (?, ?, ?)", userA, userB, adminId);
    }

    @Test
    void list_whenFilteredByMarch_returnsThreeRowsNewestFirstWithTotals() {
        // when
        AdminLedgerPage page = adminLedgerService.list(null, null, keyA, MARCH_FIRST, MARCH_LAST, 0, 20);

        // then: tháng 3 có ba dòng của hai người, tổng vào 50, tổng ra -15
        assertThat(page.totalElements()).isEqualTo(3);
        assertThat(page.items())
                .extracting(AdminLedgerRow::type)
                .containsExactly(CreditTxType.ADMIN_ADJUST, CreditTxType.POST_FEE, CreditTxType.TOP_UP);
        assertThat(page.totalIn()).isEqualTo(50);
        assertThat(page.totalOut()).isEqualTo(-15);
    }

    @Test
    void list_whenFilteredByTypeAndUser_returnsOnlyThoseRows() {
        // when
        AdminLedgerPage page = adminLedgerService.list(
                CreditTxType.TOP_UP, userA, null, MARCH_FIRST, LocalDate.of(2003, 4, 30), 0, 20);

        // then: hai dòng nạp của A, tổng vào 70, không có dòng âm
        assertThat(page.items()).hasSize(2);
        assertThat(page.totalIn()).isEqualTo(70);
        assertThat(page.totalOut()).isZero();
    }

    @Test
    void list_whenKeywordIsOrderCode_findsTheTopUpRowWithOrderCodeAndNames() {
        // when
        AdminLedgerPage page =
                adminLedgerService.list(null, null, orderCode.toLowerCase(), MARCH_FIRST, MARCH_LAST, 0, 20);

        // then
        assertThat(page.items()).hasSize(1);
        AdminLedgerRow row = page.items().get(0);
        assertThat(row.orderCode()).isEqualTo(orderCode);
        assertThat(row.userName()).isEqualTo("Lan Sổ Cái");
        assertThat(row.userEmail()).isEqualTo("ledger-a-" + keyA + "@test.local");
        assertThat(row.balanceAfter()).isEqualTo(50);
    }

    @Test
    void list_whenKeywordIsNote_findsAdminAdjustWithActorName() {
        // when
        AdminLedgerPage page = adminLedgerService.list(null, null, "trừ nhầm", MARCH_FIRST, MARCH_LAST, 0, 20);

        // then: người thực hiện chỉ có ở dòng Admin điều chỉnh
        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).createdByName()).isEqualTo("Quản Trị Sổ Cái");
        assertThat(page.items().get(0).note()).isEqualTo("Trừ nhầm lần trước");
    }

    @Test
    void list_whenFeeRowHasCreatedBy_doesNotShowActor() {
        // when
        AdminLedgerPage page =
                adminLedgerService.list(CreditTxType.POST_FEE, userA, null, MARCH_FIRST, MARCH_LAST, 0, 20);

        // then: created_by là chính người dùng nên không hiện như người thực hiện
        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).createdByName()).isNull();
    }

    @Test
    void list_whenPaging_keepsTotalsOfWholeRange() {
        // when
        AdminLedgerPage first = adminLedgerService.list(null, null, keyA, MARCH_FIRST, MARCH_LAST, 0, 2);
        AdminLedgerPage second = adminLedgerService.list(null, null, keyA, MARCH_FIRST, MARCH_LAST, 1, 2);

        // then
        assertThat(first.items()).hasSize(2);
        assertThat(second.items()).hasSize(1);
        assertThat(first.totalElements()).isEqualTo(3);
        assertThat(second.totalIn()).isEqualTo(50);
        assertThat(second.totalOut()).isEqualTo(-15);
    }

    @Test
    void list_whenFromAfterTo_throwsInvalidDateRange() {
        assertThatThrownBy(() -> adminLedgerService.list(null, null, null, MARCH_LAST, MARCH_FIRST, 0, 20))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(WalletErrorCode.WALLET_INVALID_DATE_RANGE));
    }

    @Test
    void exportCsv_hasHeaderAndOneLinePerFilteredRowWithVietnameseTypes() {
        // when
        String csv = new String(
                adminLedgerService.exportCsv(null, null, keyA, MARCH_FIRST, MARCH_LAST), StandardCharsets.UTF_8);

        // then: một dòng tiêu đề và ba dòng dữ liệu
        String[] lines = csv.split("\r\n");
        assertThat(lines).hasSize(4);
        assertThat(lines[0]).contains("Thời gian").contains("Số Credit").contains("Mã đơn nạp");
        assertThat(csv).contains("Nạp Credit").contains("Phí đăng bài").contains("Admin điều chỉnh");
        assertThat(csv).doesNotContain("TOP_UP").doesNotContain("ADMIN_ADJUST");
    }

    @Test
    void exportExcel_writesWorkbookWithFilteredRowsFormattingAndVietnameseTypes() throws Exception {
        // when
        byte[] excel = adminLedgerService.exportExcel(null, null, keyA, MARCH_FIRST, MARCH_LAST);

        // then
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
            var sheet = workbook.getSheet("Lịch sử giao dịch Credit");
            assertThat(sheet).isNotNull();
            assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("Tất cả");
            assertThat(sheet.getRow(1).getCell(3).getStringCellValue()).isEqualTo("01/03/2003");
            assertThat(sheet.getRow(1).getCell(5).getStringCellValue()).isEqualTo("31/03/2003");
            assertThat(sheet.getRow(2).getCell(1).getNumericCellValue()).isEqualTo(3.0);
            assertThat(sheet.getRow(4).getCell(0).getStringCellValue()).isEqualTo("Thời gian");
            assertThat(sheet.getRow(4).getCell(4).getStringCellValue()).isEqualTo("Số Credit");
            assertThat(sheet.getPaneInformation().isFreezePane()).isTrue();
            assertThat(sheet.getPaneInformation().getVerticalSplitPosition()).isEqualTo((short) 1);
            assertThat(sheet.getPaneInformation().getHorizontalSplitPosition()).isEqualTo((short) 5);
            assertThat(sheet.getCTWorksheet().getAutoFilter().getRef()).isEqualTo("A5:J8");
            assertThat(sheet.getLastRowNum()).isEqualTo(7);

            // Hàng 5: mới nhất trước -> ADMIN_ADJUST (-5) tại 2003-03-06T03:00:00Z = 10:00 UTC+7
            var latestRow = sheet.getRow(5);
            assertThat(latestRow.getCell(0).getLocalDateTimeCellValue()).isEqualTo(LocalDateTime.of(2003, 3, 6, 10, 0));
            assertThat(latestRow.getCell(1).getStringCellValue()).isEqualTo("Bình Sổ Cái");
            assertThat(latestRow.getCell(1).getCellStyle().getWrapText()).isTrue();
            assertThat(latestRow.getCell(3).getStringCellValue()).isEqualTo("Admin điều chỉnh");
            assertThat(latestRow.getCell(3).getCellStyle().getAlignment()).isEqualTo(HorizontalAlignment.CENTER);
            assertThat(latestRow.getCell(4).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(latestRow.getCell(4).getNumericCellValue()).isEqualTo(-5.0);
            assertThat(latestRow.getCell(4).getCellStyle().getAlignment()).isEqualTo(HorizontalAlignment.RIGHT);
            assertThat(latestRow.getCell(5).getNumericCellValue()).isEqualTo(15.0);
            assertThat(latestRow.getCell(8).getStringCellValue()).isEqualTo("Trừ nhầm lần trước");
            assertThat(latestRow.getCell(9).getStringCellValue()).isEqualTo("Quản Trị Sổ Cái");
            assertThat(latestRow.getCell(0).getCellStyle().getVerticalAlignment())
                    .isEqualTo(VerticalAlignment.CENTER);

            // Hàng 7: TOP_UP (+50) có orderCode
            var topUpRow = sheet.getRow(7);
            assertThat(topUpRow.getCell(3).getStringCellValue()).isEqualTo("Nạp Credit");
            assertThat(topUpRow.getCell(4).getNumericCellValue()).isEqualTo(50.0);
            assertThat(topUpRow.getCell(7).getStringCellValue()).isEqualTo(orderCode);
            assertThat(topUpRow.getCell(7).getCellStyle().getDataFormatString()).isEqualTo("@");
        }
    }

    private long insertUser(String email, String fullName, String phone) {
        return jdbc.queryForObject(
                "INSERT INTO users (email, password_hash, full_name, phone, status) VALUES (?, 'x', ?, ?, 'ACTIVE') RETURNING id",
                Long.class,
                email,
                fullName,
                phone);
    }

    private void insertLedger(
            long userId,
            int amount,
            String type,
            long balanceAfter,
            Long paymentOrderId,
            String note,
            Long createdBy,
            String createdAt) {
        jdbc.update(
                "INSERT INTO credit_ledger (user_id, amount, type, balance_after, payment_order_id, note, created_by, created_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?::timestamptz)",
                userId,
                amount,
                type,
                balanceAfter,
                paymentOrderId,
                note,
                createdBy,
                createdAt);
    }
}
