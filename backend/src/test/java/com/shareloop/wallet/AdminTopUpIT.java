package com.shareloop.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shareloop.common.api.PageResponse;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.support.IntegrationTest;
import com.shareloop.wallet.dto.AdminTopUpOrderDetail;
import com.shareloop.wallet.dto.AdminTopUpOrderResponse;
import com.shareloop.wallet.dto.AdminTopUpSummary;
import com.shareloop.wallet.dto.FinanceSummaryResponse;
import com.shareloop.wallet.dto.ReconcileTopUpRequest;
import com.shareloop.wallet.entity.PaymentOrder;
import com.shareloop.wallet.entity.PaymentOrderStatus;
import com.shareloop.wallet.entity.PaymentProvider;
import com.shareloop.wallet.repository.PaymentOrderRepository;
import com.shareloop.wallet.service.AdminTopUpService;
import com.shareloop.wallet.service.CreditService;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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

/** Trang Giao dịch nạp tiền của Admin: lọc, đối soát đơn đã đóng mà tiền đã về, và không lẫn vào báo cáo tiền thu. */
class AdminTopUpIT extends IntegrationTest {

    @Autowired
    AdminTopUpService adminTopUpService;

    @Autowired
    CreditService creditService;

    @Autowired
    PaymentOrderRepository orders;

    @Autowired
    JdbcTemplate jdbc;

    long adminId;
    long userId;
    String userEmail;
    final List<String> orderCodes = new ArrayList<>();

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        userEmail = "payer-" + suffix + "@test.local";
        userId = insertUser(userEmail, "Người Nạp Thử", "09" + suffix.substring(0, 8));
        adminId = insertUser("admin-" + suffix + "@test.local", "Quản Trị Thử", "08" + suffix.substring(0, 8));
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM activity_logs WHERE actor_id = ?", adminId);
        jdbc.update("DELETE FROM notifications WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM credit_ledger WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM payment_orders WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM users WHERE id IN (?, ?)", userId, adminId);
    }

    @Test
    void list_whenNeedsReconcileOnly_returnsClosedPaidUnreviewedVnpayOrders() {
        // given: một đơn cần đối soát, một đơn hết hạn không thu tiền, một đơn thành công
        String flagged = closedPaidOrder("EXPIRED");
        String unpaid = order("EXPIRED", null);
        String success = order("SUCCESS", Instant.now());

        // when
        PageResponse<AdminTopUpOrderResponse> page =
                adminTopUpService.list(PaymentProvider.VNPAY, null, userEmail, null, null, false, true, 0, 20);

        // then: chỉ đơn đã đóng mà VNPay đã thu tiền
        assertThat(page.items()).extracting(AdminTopUpOrderResponse::orderCode).containsExactly(flagged);
        assertThat(page.items().get(0).needsReconcile()).isTrue();
        assertThat(page.items().get(0).providerTxnId()).isEqualTo("123456");
        assertThat(findNeedsReconcile(unpaid)).isFalse();
        assertThat(findNeedsReconcile(success)).isFalse();
    }

    @Test
    void list_whenKeywordIsEmailOrOrderCode_matchesBoth() {
        // given
        String firstCode = order("FAILED", null);
        String secondCode = order("CANCELLED", null);

        // when
        PageResponse<AdminTopUpOrderResponse> byEmail =
                adminTopUpService.list(null, null, userEmail.toUpperCase(), null, null, false, false, 0, 20);
        PageResponse<AdminTopUpOrderResponse> byCode =
                adminTopUpService.list(null, null, firstCode.substring(0, 12), null, null, false, false, 0, 20);

        // then
        assertThat(byEmail.items())
                .extracting(AdminTopUpOrderResponse::orderCode)
                .containsExactlyInAnyOrder(firstCode, secondCode);
        assertThat(byCode.items())
                .extracting(AdminTopUpOrderResponse::orderCode)
                .contains(firstCode);
    }

    @Test
    void list_whenDateRangeExcludesToday_returnsNothingForThisUser() {
        // given
        order("FAILED", null);

        // when: khoảng ngày trong quá khứ
        PageResponse<AdminTopUpOrderResponse> page = adminTopUpService.list(
                null,
                null,
                userEmail,
                java.time.LocalDate.of(2001, 1, 1),
                java.time.LocalDate.of(2001, 1, 31),
                false,
                false,
                0,
                20);

        // then
        assertThat(page.items()).isEmpty();
    }

    @Test
    void paidDay_matchesCollectedCashAndKeepsTheSameFilterInSummaryAndExport() throws Exception {
        LocalDate day = LocalDate.of(2001, 1, 2);
        String paid = order("SUCCESS", Instant.parse("2001-01-01T17:00:00Z"));
        String nextDay = order("SUCCESS", Instant.parse("2001-01-02T17:00:00Z"));
        String pending = order("PENDING", null);
        jdbc.update(
                "UPDATE payment_orders SET created_at = '2001-01-02T08:00:00Z' WHERE order_code IN (?, ?)",
                nextDay,
                pending);

        var byPayment =
                adminTopUpService.list(null, PaymentOrderStatus.SUCCESS, userEmail, day, day, true, false, 0, 10);
        assertThat(byPayment.items())
                .extracting(AdminTopUpOrderResponse::orderCode)
                .containsExactly(paid);
        var byCreation =
                adminTopUpService.list(null, PaymentOrderStatus.SUCCESS, userEmail, day, day, false, false, 0, 10);
        assertThat(byCreation.items())
                .extracting(AdminTopUpOrderResponse::orderCode)
                .containsExactly(nextDay);
        assertThat(adminTopUpService.summarize(day, day, true).byStatus())
                .filteredOn(row -> row.status() == PaymentOrderStatus.SUCCESS)
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.orderCount()).isEqualTo(1);
                    assertThat(row.amountVnd()).isEqualTo(20000);
                });
        String csv = new String(
                adminTopUpService.exportCsv(null, PaymentOrderStatus.SUCCESS, userEmail, day, day, true, false),
                StandardCharsets.UTF_8);
        assertThat(csv).contains(paid).doesNotContain(nextDay, pending);

        jdbc.update("UPDATE payment_orders SET provider_txn_id = '0000123' WHERE order_code = ?", paid);
        byte[] excel =
                adminTopUpService.exportExcel(null, PaymentOrderStatus.SUCCESS, userEmail, day, day, true, false);
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
            var sheet = workbook.getSheet("Giao dịch nạp tiền");
            assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("Ngày thanh toán");
            assertThat(sheet.getRow(4).getCell(3).getStringCellValue()).isEqualTo("Số tiền (VND)");
            assertThat(sheet.getPaneInformation().isFreezePane()).isTrue();
            assertThat(sheet.getPaneInformation().getVerticalSplitPosition()).isEqualTo((short) 1);
            assertThat(sheet.getPaneInformation().getHorizontalSplitPosition()).isEqualTo((short) 5);
            assertThat(sheet.getCTWorksheet().getAutoFilter().getRef()).isEqualTo("A5:L6");
            assertThat(sheet.getLastRowNum()).isEqualTo(5);
            var row = sheet.getRow(5);
            assertThat(row.getCell(0).getStringCellValue()).isEqualTo(paid);
            assertThat(row.getCell(3).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(row.getCell(3).getNumericCellValue()).isEqualTo(20000.0);
            assertThat(row.getCell(3).getCellStyle().getAlignment()).isEqualTo(HorizontalAlignment.RIGHT);
            assertThat(row.getCell(6).getCellStyle().getAlignment()).isEqualTo(HorizontalAlignment.CENTER);
            assertThat(row.getCell(0).getCellStyle().getVerticalAlignment()).isEqualTo(VerticalAlignment.CENTER);
            assertThat(row.getCell(11).getCellStyle().getWrapText()).isTrue();
            assertThat(row.getCell(4).getNumericCellValue()).isEqualTo(20.0);
            assertThat(row.getCell(6).getStringCellValue()).isEqualTo("Thành công");
            assertThat(row.getCell(8).getStringCellValue()).isEqualTo("0000123");
            assertThat(row.getCell(8).getCellStyle().getDataFormatString()).isEqualTo("@");
            assertThat(row.getCell(10).getLocalDateTimeCellValue()).isEqualTo(LocalDateTime.of(2001, 1, 2, 0, 0));
        }
    }

    @Test
    void reconcile_grantCredit_creditsOnceAndSecondCallThrowsNotReconcilable() {
        // given
        String code = closedPaidOrder("EXPIRED");

        // when
        AdminTopUpOrderDetail detail = adminTopUpService.reconcile(
                adminId,
                code,
                new ReconcileTopUpRequest(ReconcileTopUpRequest.Action.GRANT_CREDIT, "Đã đối chiếu sao kê"));

        // then: đơn SUCCESS, cộng đúng 20 Credit một lần, ledger TOP_UP gắn đơn, ghi người xử lý và nhật ký
        assertThat(detail.order().status()).isEqualTo(PaymentOrderStatus.SUCCESS);
        assertThat(detail.order().needsReconcile()).isFalse();
        assertThat(detail.order().reviewedByName()).isEqualTo("Quản Trị Thử");
        assertThat(detail.ledgerLines()).hasSize(1);
        assertThat(creditService.getWallet(userId).creditBalance()).isEqualTo(20);
        assertThat(count(
                        "SELECT count(*) FROM activity_logs WHERE actor_id = ? AND type = 'ADMIN_TOPUP_RECONCILED'",
                        adminId))
                .isEqualTo(1);
        assertThat(count("SELECT count(*) FROM notifications WHERE user_id = ? AND type = 'TOPUP_RECONCILED'", userId))
                .isEqualTo(1);
        assertThatThrownBy(() -> adminTopUpService.reconcile(
                        adminId,
                        code,
                        new ReconcileTopUpRequest(ReconcileTopUpRequest.Action.GRANT_CREDIT, "Bấm lần hai")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(WalletErrorCode.WALLET_TOPUP_NOT_RECONCILABLE));
        assertThat(creditService.getWallet(userId).creditBalance()).isEqualTo(20);
    }

    @Test
    void reconcile_refundedOutside_keepsStatusWithoutCreditAndOnlyOnce() {
        // given
        String code = closedPaidOrder("CANCELLED");

        // when
        AdminTopUpOrderDetail detail = adminTopUpService.reconcile(
                adminId,
                code,
                new ReconcileTopUpRequest(ReconcileTopUpRequest.Action.REFUNDED_OUTSIDE, "Đã hoàn qua ngân hàng"));

        // then
        assertThat(detail.order().status()).isEqualTo(PaymentOrderStatus.CANCELLED);
        assertThat(detail.order().needsReconcile()).isFalse();
        assertThat(detail.order().reviewNote()).isEqualTo("Đã hoàn qua ngân hàng");
        assertThat(creditService.getWallet(userId).creditBalance()).isZero();
        assertThat(detail.ledgerLines()).isEmpty();
        assertThatThrownBy(() -> adminTopUpService.reconcile(
                        adminId,
                        code,
                        new ReconcileTopUpRequest(ReconcileTopUpRequest.Action.GRANT_CREDIT, "Đổi ý sau khi hoàn")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(WalletErrorCode.WALLET_TOPUP_NOT_RECONCILABLE));
        assertThat(creditService.getWallet(userId).creditBalance()).isZero();
    }

    @Test
    void reconcile_whenOrderNeverPaid_throwsNotReconcilable() {
        // given: đơn hết hạn nhưng VNPay không thu tiền
        String code = order("EXPIRED", null);

        // when, then
        assertThatThrownBy(() -> adminTopUpService.reconcile(
                        adminId,
                        code,
                        new ReconcileTopUpRequest(ReconcileTopUpRequest.Action.GRANT_CREDIT, "Thử cộng nhầm")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(WalletErrorCode.WALLET_TOPUP_NOT_RECONCILABLE));
        assertThat(creditService.getWallet(userId).creditBalance()).isZero();
    }

    @Test
    void getDetail_returnsOnlySafeCallbackKeys() {
        // given: callback có cả chữ ký bảo mật và một khoá lạ
        String code = closedPaidOrder("FAILED");
        jdbc.update(
                "UPDATE payment_orders SET callback_data = ?::jsonb WHERE order_code = ?",
                "{\"vnp_ResponseCode\":\"00\",\"vnp_TransactionStatus\":\"00\",\"vnp_TransactionNo\":\"123456\","
                        + "\"vnp_BankCode\":\"NCB\",\"vnp_SecureHash\":\"abc\",\"vnp_TmnCode\":\"SECRET\"}",
                code);

        // when
        AdminTopUpOrderDetail detail = adminTopUpService.getDetail(code);

        // then
        assertThat(detail.callbackData())
                .containsOnlyKeys("vnp_ResponseCode", "vnp_TransactionStatus", "vnp_TransactionNo", "vnp_BankCode");
    }

    @Test
    void closedPaidOrder_isNotCountedAsCashInFinanceReport() {
        // given: báo cáo tiền thu trước khi có đơn đã đóng
        Instant now = Instant.now();
        Instant from = now.minusSeconds(3600);
        Instant to = now.plusSeconds(3600);
        FinanceSummaryResponse before = creditService.getFinanceSummary(from, to);

        // when: đơn đã đóng nhưng có paid_at do VNPay báo, chưa nạp thành công
        closedPaidOrder("EXPIRED");
        FinanceSummaryResponse after = creditService.getFinanceSummary(from, to);

        // then: báo cáo tiền thu chỉ tính đơn SUCCESS nên không đổi
        assertThat(after.cashOrderCount()).isEqualTo(before.cashOrderCount());
        assertThat(after.cashAmountVnd()).isEqualTo(before.cashAmountVnd());
    }

    @Test
    void summarize_countsOrdersByStatusAndNeedsReconcile() {
        // given
        closedPaidOrder("EXPIRED");
        order("FAILED", null);

        // when
        AdminTopUpSummary summary = adminTopUpService.summarize(null, null, false);

        // then: toàn hệ thống nên chỉ kiểm tối thiểu, đủ năm trạng thái và ít nhất một đơn cần đối soát
        assertThat(summary.byStatus()).hasSize(PaymentOrderStatus.values().length);
        assertThat(summary.needsReconcileCount()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void backfillMigration_setsPaidAtOnlyWhenStoredCallbackProvesPayment() throws Exception {
        // given: ba đơn cũ chưa có paid_at. Đơn một có callback thành công đúng chữ ký, đơn hai chỉ có ghi chú, đơn ba
        // có
        // callback thất bại (khách hủy).
        String proven = order("EXPIRED", null);
        String noteOnly = order("EXPIRED", null);
        String customerCancelled = order("CANCELLED", null);
        jdbc.update(
                "UPDATE payment_orders SET signature_verified = TRUE,"
                        + " callback_data = '{\"vnp_ResponseCode\":\"00\",\"vnp_TransactionStatus\":\"00\"}'::jsonb"
                        + " WHERE order_code = ?",
                proven);
        jdbc.update(
                "UPDATE payment_orders SET review_note = 'VNPay báo đã thu tiền sau khi đơn đã đóng' WHERE order_code = ?",
                noteOnly);
        jdbc.update(
                "UPDATE payment_orders SET signature_verified = TRUE,"
                        + " callback_data = '{\"vnp_ResponseCode\":\"24\",\"vnp_TransactionStatus\":\"02\"}'::jsonb"
                        + " WHERE order_code = ?",
                customerCancelled);

        // when: chạy lại đúng câu lệnh của migration
        jdbc.execute(java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/resources/db/migration/V202611221000__wallet_payment_order_backfill_paid_at.sql")));

        // then: chỉ đơn có bằng chứng chắc chắn mới có paid_at
        assertThat(paidAtOf(proven)).isNotNull();
        assertThat(paidAtOf(noteOnly)).isNull();
        assertThat(paidAtOf(customerCancelled)).isNull();
    }

    @Test
    void rewordMigration_replacesOldSemicolonNoteAndKeepsOtherNotes() throws Exception {
        // given: một đơn mang câu cũ có dấu chấm phẩy, một đơn mang ghi chú khác
        String oldNote = order("EXPIRED", null);
        String otherNote = order("EXPIRED", null);
        jdbc.update(
                "UPDATE payment_orders SET review_note = 'VNPay báo đã thu tiền sau khi đơn đã đóng; đơn chờ đối soát để hoàn tiền (mã giao dịch VNPay: 123).' WHERE order_code = ?",
                oldNote);
        jdbc.update("UPDATE payment_orders SET review_note = 'Đã hoàn qua ngân hàng' WHERE order_code = ?", otherNote);

        // when: chạy lại đúng câu lệnh của migration
        jdbc.execute(java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/resources/db/migration/V202611221020__wallet_payment_order_reword_review_note.sql")));

        // then
        assertThat(noteOf(oldNote))
                .isEqualTo(
                        "VNPay báo đã thu tiền sau khi đơn đã đóng. Đơn chờ Admin đối soát (mã giao dịch VNPay: 123).");
        assertThat(noteOf(otherNote)).isEqualTo("Đã hoàn qua ngân hàng");
    }

    private String noteOf(String orderCode) {
        return jdbc.queryForObject(
                "SELECT review_note FROM payment_orders WHERE order_code = ?", String.class, orderCode);
    }

    private Object paidAtOf(String orderCode) {
        return jdbc.queryForObject("SELECT paid_at FROM payment_orders WHERE order_code = ?", Object.class, orderCode);
    }

    // Đơn VNPAY đã đóng mà VNPay đã báo thu tiền: có paid_at và mã giao dịch, chưa ai xử lý.
    private String closedPaidOrder(String status) {
        String code = order(status, Instant.now());
        jdbc.update("UPDATE payment_orders SET provider_txn_id = '123456' WHERE order_code = ?", code);
        return code;
    }

    private String order(String status, Instant paidAt) {
        String code = UUID.randomUUID().toString().replace("-", "");
        orders.save(new PaymentOrder(userId, code, 20000, 20, 1000, PaymentProvider.VNPAY));
        jdbc.update(
                "UPDATE payment_orders SET status = ?, paid_at = ? WHERE order_code = ?",
                status,
                paidAt == null ? null : java.sql.Timestamp.from(paidAt),
                code);
        orderCodes.add(code);
        return code;
    }

    private boolean findNeedsReconcile(String orderCode) {
        return adminTopUpService.getDetail(orderCode).order().needsReconcile();
    }

    private long insertUser(String email, String fullName, String phone) {
        return jdbc.queryForObject(
                "INSERT INTO users (email, password_hash, full_name, phone, status) VALUES (?, 'test-hash', ?, ?, 'ACTIVE') RETURNING id",
                Long.class,
                email,
                fullName,
                phone);
    }

    private long count(String sql, Object argument) {
        return jdbc.queryForObject(sql, Long.class, argument);
    }
}
