package com.shareloop.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.shareloop.common.exception.BusinessException;
import com.shareloop.support.IntegrationTest;
import com.shareloop.wallet.dto.CreditTypeTotal;
import com.shareloop.wallet.dto.DailyCashTotal;
import com.shareloop.wallet.dto.FinanceSummaryResponse;
import com.shareloop.wallet.dto.TopUpReconciliation;
import com.shareloop.wallet.entity.CreditTxType;
import com.shareloop.wallet.service.CreditService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Test báo cáo tài chính của CreditService với PostgreSQL thật. Báo cáo cộng dữ liệu của mọi người dùng, nên mỗi test
 * dùng một khoảng ngày năm 1999–2001 mà test khác không ghi vào (test khác dùng giờ hiện tại hoặc năm 2026); nhờ vậy
 * số liệu trong khoảng chỉ là dữ liệu mẫu của chính test.
 */
class CreditServiceFinanceSummaryIT extends IntegrationTest {

    private static final AtomicInteger PHONE_SEQUENCE = new AtomicInteger();

    @Autowired
    CreditService creditService;

    @Autowired
    JdbcTemplate jdbc;

    private final List<Long> createdUserIds = new ArrayList<>();

    // Thứ tự xoá: credit_ledger tham chiếu payment_orders, cả hai tham chiếu users.
    @AfterEach
    void cleanUp() {
        for (Long userId : createdUserIds) {
            jdbc.update("DELETE FROM credit_ledger WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM payment_orders WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM users WHERE id = ?", userId);
        }
        createdUserIds.clear();
    }

    @Test
    void getFinanceSummary_withSampleData_returnsTotalsMatchingHandCalculation() {
        // given: khoảng tháng 3/2001, [from, to)
        Instant from = Instant.parse("2001-03-01T00:00:00Z");
        Instant to = Instant.parse("2001-04-01T00:00:00Z");
        long userId = insertUser();
        long otherUserId = insertUser();

        // Đơn SUCCESS trong khoảng: 20 + 30 + 50 = 100 Credit, 100.000đ. Đơn đầu tạo tháng 2 nhưng thanh toán đúng mốc
        // from nên vẫn tính (lọc theo paid_at, mốc from được gồm).
        long firstOrderId = insertPaidOrder(userId, 20, "2001-02-28T23:50:00Z", "2001-03-01T00:00:00Z");
        long secondOrderId = insertPaidOrder(otherUserId, 30, "2001-03-15T10:00:00Z", "2001-03-15T10:01:00Z");
        long thirdOrderId = insertPaidOrder(userId, 50, "2001-03-31T23:50:00Z", "2001-03-31T23:59:59Z");
        // Đơn SUCCESS ngoài khoảng: thanh toán đúng mốc to (không gồm) và trước from.
        long orderAtEndId = insertPaidOrder(userId, 40, "2001-03-31T23:55:00Z", "2001-04-01T00:00:00Z");
        insertPaidOrder(userId, 10, "2001-02-28T23:00:00Z", "2001-02-28T23:59:59Z");
        // Đơn chưa thanh toán tạo trong khoảng: không phải tiền mặt thực thu.
        insertUnpaidOrder(userId, 25, "PENDING", "2001-03-10T00:00:00Z");
        insertUnpaidOrder(otherUserId, 15, "FAILED", "2001-03-11T00:00:00Z");

        // Credit cộng vào trong khoảng: TOP_UP 100 (3 dòng) + ADMIN_ADJUST 7 (1 dòng) = 107.
        insertTopUpLedger(userId, 20, "2001-03-01T00:00:00Z", firstOrderId);
        insertTopUpLedger(otherUserId, 30, "2001-03-15T10:01:00Z", secondOrderId);
        insertTopUpLedger(userId, 50, "2001-03-31T23:59:59Z", thirdOrderId);
        insertTopUpLedger(userId, 40, "2001-04-01T00:00:00Z", orderAtEndId);
        insertLedger(userId, 7, "ADMIN_ADJUST", "2001-03-10T00:00:00Z");

        // Credit trừ ra trong khoảng: AI 2 (2 dòng), EDIT 5, POST 10 (2 dòng), RENEW 5 = 22.
        insertLedger(userId, -5, "POST_FEE", "2001-03-02T00:00:00Z");
        insertLedger(otherUserId, -5, "POST_FEE", "2001-03-03T00:00:00Z");
        insertLedger(userId, -5, "EDIT_FEE", "2001-03-04T00:00:00Z");
        insertLedger(userId, -5, "RENEW_FEE", "2001-03-05T00:00:00Z");
        insertLedger(userId, -1, "AI_SEARCH_FEE", "2001-03-06T00:00:00Z");
        insertLedger(otherUserId, -1, "AI_SEARCH_FEE", "2001-03-07T00:00:00Z");
        // Ngoài khoảng: không được tính.
        insertLedger(userId, -5, "BOOST_FEE", "2001-02-28T23:59:59Z");
        insertLedger(userId, -5, "POST_FEE", "2001-04-01T00:00:00Z");

        // when
        FinanceSummaryResponse summary = creditService.getFinanceSummary(from, to);

        // then
        assertThat(summary.from()).isEqualTo(from);
        assertThat(summary.to()).isEqualTo(to);
        assertThat(summary.cashOrderCount()).isEqualTo(3);
        assertThat(summary.cashAmountVnd()).isEqualTo(100_000);
        assertThat(summary.creditInTotal()).isEqualTo(107);
        assertThat(summary.creditInByType())
                .containsExactly(
                        new CreditTypeTotal(CreditTxType.ADMIN_ADJUST, 7, 1),
                        new CreditTypeTotal(CreditTxType.TOP_UP, 100, 3));
        assertThat(summary.creditOutTotal()).isEqualTo(22);
        assertThat(summary.creditOutByType())
                .containsExactly(
                        new CreditTypeTotal(CreditTxType.AI_SEARCH_FEE, 2, 2),
                        new CreditTypeTotal(CreditTxType.EDIT_FEE, 5, 1),
                        new CreditTypeTotal(CreditTxType.POST_FEE, 10, 2),
                        new CreditTypeTotal(CreditTxType.RENEW_FEE, 5, 1));
        assertThat(summary.reconciliation()).isEqualTo(new TopUpReconciliation(100, 100, 0, true));
        // Chia theo ngày giờ Việt Nam: đơn thanh toán lúc 23:59:59 UTC ngày 31/3 đã là sáng 1/4 ở Việt Nam.
        assertThat(summary.cashByDay())
                .containsExactly(
                        new DailyCashTotal(LocalDate.parse("2001-03-01"), 20_000, 1),
                        new DailyCashTotal(LocalDate.parse("2001-03-15"), 30_000, 1),
                        new DailyCashTotal(LocalDate.parse("2001-04-01"), 50_000, 1));
    }

    @Test
    void getFinanceSummary_whenTwoOrdersPaidOnSameVietnamDay_sumsThemIntoOneDay() {
        // given: 16:30 UTC ngày 10/6 là 23:30 ngày 10/6 ở Việt Nam, 17:30 UTC đã sang ngày 11/6
        Instant from = Instant.parse("2001-06-01T00:00:00Z");
        Instant to = Instant.parse("2001-07-01T00:00:00Z");
        long userId = insertUser();
        insertPaidOrder(userId, 20, "2001-06-10T01:00:00Z", "2001-06-10T02:00:00Z");
        insertPaidOrder(userId, 30, "2001-06-10T16:00:00Z", "2001-06-10T16:30:00Z");
        insertPaidOrder(userId, 40, "2001-06-10T17:00:00Z", "2001-06-10T17:30:00Z");

        // when
        FinanceSummaryResponse summary = creditService.getFinanceSummary(from, to);

        // then
        assertThat(summary.cashByDay())
                .containsExactly(
                        new DailyCashTotal(LocalDate.parse("2001-06-10"), 50_000, 2),
                        new DailyCashTotal(LocalDate.parse("2001-06-11"), 40_000, 1));
    }

    @Test
    void getFinanceSummary_whenSuccessOrderHasNoTopUpEntry_reportsMismatch() {
        // given: hai đơn SUCCESS (20 + 30) nhưng chỉ đơn 20 được cộng vào sổ cái
        Instant from = Instant.parse("2001-06-01T00:00:00Z");
        Instant to = Instant.parse("2001-07-01T00:00:00Z");
        long userId = insertUser();
        long creditedOrderId = insertPaidOrder(userId, 20, "2001-06-10T00:00:00Z", "2001-06-10T00:01:00Z");
        insertPaidOrder(userId, 30, "2001-06-11T00:00:00Z", "2001-06-11T00:01:00Z");
        insertTopUpLedger(userId, 20, "2001-06-10T00:01:00Z", creditedOrderId);

        // when
        FinanceSummaryResponse summary = creditService.getFinanceSummary(from, to);

        // then: thu 50 Credit tiền mặt nhưng chỉ cộng 20, lệch 30
        assertThat(summary.reconciliation()).isEqualTo(new TopUpReconciliation(50, 20, 30, false));
    }

    @Test
    void getFinanceSummary_whenNoDataInRange_returnsZerosAndMatched() {
        // given: khoảng năm 1999 không có dữ liệu nào
        Instant from = Instant.parse("1999-01-01T00:00:00Z");
        Instant to = Instant.parse("1999-02-01T00:00:00Z");

        // when
        FinanceSummaryResponse summary = creditService.getFinanceSummary(from, to);

        // then
        assertThat(summary.cashOrderCount()).isZero();
        assertThat(summary.cashAmountVnd()).isZero();
        assertThat(summary.creditInTotal()).isZero();
        assertThat(summary.creditInByType()).isEmpty();
        assertThat(summary.creditOutTotal()).isZero();
        assertThat(summary.creditOutByType()).isEmpty();
        assertThat(summary.reconciliation()).isEqualTo(new TopUpReconciliation(0, 0, 0, true));
        assertThat(summary.cashByDay()).isEmpty();
    }

    @Test
    void getFinanceSummary_whenFromNotBeforeTo_throwsInvalidDateRange() {
        // given
        Instant sameMoment = Instant.parse("2001-03-01T00:00:00Z");

        // when
        Throwable thrown = catchThrowable(() -> creditService.getFinanceSummary(sameMoment, sameMoment));

        // then
        assertThat(thrown)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(WalletErrorCode.WALLET_INVALID_DATE_RANGE));
    }

    // ---------- dữ liệu mẫu ----------

    private long insertUser() {
        // Email và phone có unique index nên mỗi user một giá trị riêng; đầu số 0996 khác các lớp test khác.
        int sequence = PHONE_SEQUENCE.incrementAndGet();
        String email = "finance-it-" + sequence + "-" + System.nanoTime() + "@test.local";
        String phone = String.format("0996%06d", sequence);
        Long userId = jdbc.queryForObject(
                "INSERT INTO users (email, password_hash, full_name, phone, status)"
                        + " VALUES (?, 'not-a-real-hash', 'Finance IT', ?, 'ACTIVE') RETURNING id",
                Long.class,
                email,
                phone);
        createdUserIds.add(userId);
        return userId;
    }

    // Đơn SUCCESS với created_at, paid_at chọn trước. amount_vnd = credits x 1.000 nên credits phải >= 10 (CHECK DB).
    private long insertPaidOrder(long userId, int credits, String createdAt, String paidAt) {
        return jdbc.queryForObject(
                "INSERT INTO payment_orders (user_id, order_code, amount_vnd, credits, vnd_per_credit, provider,"
                        + " status, signature_verified, created_at, paid_at)"
                        + " VALUES (?, ?, ?, ?, 1000, 'MOCK', 'SUCCESS', TRUE, ?::timestamptz, ?::timestamptz)"
                        + " RETURNING id",
                Long.class,
                userId,
                newOrderCode(),
                credits * 1000L,
                credits,
                createdAt,
                paidAt);
    }

    // Đơn chưa thanh toán (PENDING, FAILED...) nên không có paid_at.
    private void insertUnpaidOrder(long userId, int credits, String status, String createdAt) {
        jdbc.update(
                "INSERT INTO payment_orders (user_id, order_code, amount_vnd, credits, vnd_per_credit, provider,"
                        + " status, created_at) VALUES (?, ?, ?, ?, 1000, 'MOCK', ?, ?::timestamptz)",
                userId,
                newOrderCode(),
                credits * 1000L,
                credits,
                status,
                createdAt);
    }

    // balance_after không ảnh hưởng báo cáo nên ghi 0 cho gọn.
    private void insertLedger(long userId, int amount, String type, String createdAt) {
        jdbc.update(
                "INSERT INTO credit_ledger (user_id, amount, type, balance_after, created_at)"
                        + " VALUES (?, ?, ?, 0, ?::timestamptz)",
                userId,
                amount,
                type,
                createdAt);
    }

    private void insertTopUpLedger(long userId, int credits, String createdAt, long paymentOrderId) {
        jdbc.update(
                "INSERT INTO credit_ledger (user_id, amount, type, balance_after, payment_order_id, created_at)"
                        + " VALUES (?, ?, 'TOP_UP', 0, ?, ?::timestamptz)",
                userId,
                credits,
                paymentOrderId,
                createdAt);
    }

    private String newOrderCode() {
        return "fin-it-" + UUID.randomUUID().toString().replace("-", "");
    }
}
