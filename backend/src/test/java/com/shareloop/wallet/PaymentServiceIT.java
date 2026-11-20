package com.shareloop.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.within;

import com.shareloop.common.exception.BusinessException;
import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.service.ConfigService;
import com.shareloop.support.IntegrationTest;
import com.shareloop.wallet.dto.TopUpConfirmResponse;
import com.shareloop.wallet.entity.PaymentOrderStatus;
import com.shareloop.wallet.service.PaymentExpiryService;
import com.shareloop.wallet.service.PaymentService;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Test luồng nạp đầy đủ (tạo đơn → xác nhận) với PostgreSQL thật, tập trung vào việc một đơn chỉ được cộng một lần.
 * Không @Transactional ở lớp test: mỗi lời gọi service commit thật, dữ liệu tự tạo được dọn trong @AfterEach.
 */
class PaymentServiceIT extends IntegrationTest {

    private static final AtomicInteger PHONE_SEQUENCE = new AtomicInteger();

    @Autowired
    PaymentService paymentService;

    @Autowired
    PaymentExpiryService paymentExpiryService;

    @Autowired
    ConfigService configService;

    @Autowired
    JdbcTemplate jdbc;

    // Khớp app.payment.vnpay.expiry-grace-minutes trong application.yml.
    private static final long GRACE_MINUTES = 2;

    private final List<Long> createdUserIds = new ArrayList<>();

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
    void confirmMockPayment_whenCalledTwiceInSequence_creditsOnce() {
        // given
        long userId = insertUser();
        String orderCode = paymentService.createTopUpOrder(userId, 20_000).orderCode();

        // when
        TopUpConfirmResponse firstResponse = paymentService.confirmMockPayment(userId, orderCode);
        TopUpConfirmResponse secondResponse = paymentService.confirmMockPayment(userId, orderCode);

        // then: lần hai trả cùng kết quả, không cộng thêm
        assertThat(firstResponse.wallet().creditBalance()).isEqualTo(20);
        assertThat(secondResponse.status()).isEqualTo(PaymentOrderStatus.SUCCESS);
        assertThat(secondResponse.wallet().creditBalance()).isEqualTo(20);
        assertThat(balanceOf(userId)).isEqualTo(20);
        assertThat(ledgerCountOf(userId)).isEqualTo(1);
    }

    @Test
    void confirmMockPayment_whenTwoThreadsConfirmSameOrder_creditsOnceAndBothReturnSuccess() throws Exception {
        // given
        long userId = insertUser();
        String orderCode = paymentService.createTopUpOrder(userId, 20_000).orderCode();
        CountDownLatch bothReady = new CountDownLatch(2);
        CountDownLatch startSignal = new CountDownLatch(1);
        Callable<TopUpConfirmResponse> confirmTask = () -> {
            bothReady.countDown();
            startSignal.await();
            // Gọi qua bean thật: mỗi luồng có transaction riêng, tranh nhau khoá dòng users rồi dòng đơn.
            return paymentService.confirmMockPayment(userId, orderCode);
        };

        // when: hai luồng xác nhận cùng một đơn một lúc
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<TopUpConfirmResponse> responses = new ArrayList<>();
        try {
            Future<TopUpConfirmResponse> first = executor.submit(confirmTask);
            Future<TopUpConfirmResponse> second = executor.submit(confirmTask);
            bothReady.await();
            startSignal.countDown();
            responses.add(first.get(30, TimeUnit.SECONDS));
            responses.add(second.get(30, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }

        // then: cả hai đều SUCCESS, nhưng chỉ một dòng ledger và số dư chỉ cộng 20 một lần
        assertThat(responses)
                .extracting(TopUpConfirmResponse::status)
                .containsExactly(PaymentOrderStatus.SUCCESS, PaymentOrderStatus.SUCCESS);
        assertThat(ledgerCountOf(userId)).isEqualTo(1);
        assertThat(balanceOf(userId)).isEqualTo(20);
    }

    @Test
    void confirmMockPayment_whenOrderExpiredByJob_throwsNotPayable() {
        // given: đơn tạo quá hạn rồi bị job hết hạn chuyển EXPIRED
        long userId = insertUser();
        String orderCode = paymentService.createTopUpOrder(userId, 20_000).orderCode();
        long expireMinutes = configService.getInt(ConfigKey.PAYMENT_ORDER_EXPIRE_MINUTES);
        Instant pastDeadline = Instant.now().minus(expireMinutes + 1, ChronoUnit.MINUTES);
        jdbc.update(
                "UPDATE payment_orders SET created_at = ? WHERE order_code = ?",
                Timestamp.from(pastDeadline),
                orderCode);
        paymentExpiryService.expireStaleOrders(Instant.now());

        // when
        Throwable thrown = catchThrowable(() -> paymentService.confirmMockPayment(userId, orderCode));

        // then: bị từ chối, không cộng Credit, đơn vẫn EXPIRED
        assertThat(thrown)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getErrorCode())
                                .isEqualTo(WalletErrorCode.WALLET_PAYMENT_ORDER_NOT_PAYABLE));
        assertThat(balanceOf(userId)).isZero();
        assertThat(ledgerCountOf(userId)).isZero();
        assertThat(jdbc.queryForObject(
                        "SELECT status FROM payment_orders WHERE order_code = ?", String.class, orderCode))
                .isEqualTo("EXPIRED");
    }

    // ---------- hạn thanh toán và xem đơn (B29) ----------

    @Test
    void createTopUpOrder_returnsExpiresAtFromCreatedAtPlusConfiguredMinutes() {
        // given
        long userId = insertUser();

        // when
        var response = paymentService.createTopUpOrder(userId, 20_000);

        // then: expiresAt = created_at + payment_order_expire_minutes, làm tròn xuống giây như vnp_ExpireDate;
        // serverTime là giờ server lúc trả response để FE bù lệch đồng hồ
        Instant createdAt = jdbc.queryForObject(
                        "SELECT created_at FROM payment_orders WHERE order_code = ?",
                        Timestamp.class,
                        response.orderCode())
                .toInstant();
        long expireMinutes = configService.getInt(ConfigKey.PAYMENT_ORDER_EXPIRE_MINUTES);
        assertThat(response.expiresAt())
                .isEqualTo(createdAt.plus(expireMinutes, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.SECONDS));
        assertThat(response.serverTime()).isCloseTo(Instant.now(), within(10, ChronoUnit.SECONDS));
    }

    @Test
    void getOwnOrder_whenPastExpiresAtAndGrace_expiresOrderOnRead() {
        // given: đơn PENDING đã qua hạn cộng dung sai 2 phút với VNPay, job chưa chạy
        long userId = insertUser();
        String orderCode = paymentService.createTopUpOrder(userId, 20_000).orderCode();
        long expireMinutes = configService.getInt(ConfigKey.PAYMENT_ORDER_EXPIRE_MINUTES);
        Instant createdAt = Instant.now()
                .minus(expireMinutes + GRACE_MINUTES + 1, ChronoUnit.MINUTES)
                .truncatedTo(ChronoUnit.SECONDS);
        jdbc.update(
                "UPDATE payment_orders SET created_at = ? WHERE order_code = ?", Timestamp.from(createdAt), orderCode);

        // when
        var order = paymentService.getOwnOrder(userId, orderCode);

        // then: chuyển EXPIRED ngay khi xem, không đụng ví
        assertThat(order.status()).isEqualTo(PaymentOrderStatus.EXPIRED);
        assertThat(jdbc.queryForObject(
                        "SELECT status FROM payment_orders WHERE order_code = ?", String.class, orderCode))
                .isEqualTo("EXPIRED");
        assertThat(balanceOf(userId)).isZero();
        assertThat(ledgerCountOf(userId)).isZero();
    }

    @Test
    void getOwnOrder_whenAlreadyPaid_neverExpiresEvenIfOld() {
        // given: đơn đã SUCCESS từ lâu
        long userId = insertUser();
        String orderCode = paymentService.createTopUpOrder(userId, 20_000).orderCode();
        paymentService.confirmMockPayment(userId, orderCode);
        jdbc.update("UPDATE payment_orders SET created_at = now() - interval '2 days' WHERE order_code = ?", orderCode);

        // when + then
        assertThat(paymentService.getOwnOrder(userId, orderCode).status()).isEqualTo(PaymentOrderStatus.SUCCESS);
        assertThat(balanceOf(userId)).isEqualTo(20);
    }

    @Test
    void getOwnOrder_whenPastExpiresAtButWithinGrace_staysPending() {
        // given: đơn đã quá hạn 1 phút, còn trong dung sai 2 phút chờ IPN tới muộn
        long userId = insertUser();
        String orderCode = paymentService.createTopUpOrder(userId, 20_000).orderCode();
        long expireMinutes = configService.getInt(ConfigKey.PAYMENT_ORDER_EXPIRE_MINUTES);
        Instant createdAt =
                Instant.now().minus(expireMinutes + 1, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.SECONDS);
        jdbc.update(
                "UPDATE payment_orders SET created_at = ? WHERE order_code = ?", Timestamp.from(createdAt), orderCode);

        // when
        var order = paymentService.getOwnOrder(userId, orderCode);

        // then: vẫn PENDING để IPN hợp lệ tới muộn còn được cộng; expiresAt đã qua để FE hiện "Đơn đã hết hạn"
        assertThat(order.status()).isEqualTo(PaymentOrderStatus.PENDING);
        assertThat(order.expiresAt()).isEqualTo(createdAt.plus(expireMinutes, ChronoUnit.MINUTES));
        assertThat(order.userName()).isNull();
    }

    @Test
    void getOwnOrder_whenOrderOfOtherUser_throwsNotFound() {
        // given
        long ownerId = insertUser();
        long otherUserId = insertUser();
        String orderCode = paymentService.createTopUpOrder(ownerId, 20_000).orderCode();

        // when
        Throwable thrown = catchThrowable(() -> paymentService.getOwnOrder(otherUserId, orderCode));

        // then
        assertThat(thrown)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(WalletErrorCode.WALLET_PAYMENT_ORDER_NOT_FOUND));
    }

    // ---------- đơn đang chờ, 1 đơn mỗi người, lịch sử nạp ----------

    @Test
    void pendingOrder_afterLeavingAndComingBack_isStillListedWithSamePaymentUrl() {
        // given (a): tạo đơn rồi "rời trang" (FE không giữ gì)
        long userId = insertUser();
        var created = paymentService.createTopUpOrder(userId, 20_000);

        // when: quay lại trang, hỏi danh sách đơn đang chờ
        var pending = paymentService.listOwnOrders(userId, PaymentOrderStatus.PENDING, 0, 10);

        // then: vẫn thấy đúng đơn đó, kèm đúng URL thanh toán đã dựng lúc tạo (không tạo mã giao dịch mới)
        assertThat(pending.items()).hasSize(1);
        var order = pending.items().get(0);
        assertThat(order.orderCode()).isEqualTo(created.orderCode());
        assertThat(order.paymentUrl()).isEqualTo(created.redirectUrl());
        assertThat(order.expiresAt()).isEqualTo(created.expiresAt());
        assertThat(order.serverTime()).isNotNull();
        assertThat(jdbc.queryForObject(
                        "SELECT payment_url FROM payment_orders WHERE order_code = ?",
                        String.class,
                        created.orderCode()))
                .isEqualTo(created.redirectUrl());
    }

    @Test
    void createTopUpOrder_whenPendingExists_throws409WithPendingOrderAndKeepsIt() {
        // given (b): còn một đơn đang chờ
        long userId = insertUser();
        String oldCode = paymentService.createTopUpOrder(userId, 50_000).orderCode();

        // when
        Throwable thrown = catchThrowable(() -> paymentService.createTopUpOrder(userId, 20_000));

        // then: 409 kèm đơn cũ; đơn cũ vẫn PENDING, không có đơn mới
        assertThat(thrown).isInstanceOfSatisfying(BusinessException.class, ex -> {
            assertThat(ex.getErrorCode()).isEqualTo(WalletErrorCode.WALLET_TOPUP_PENDING_EXISTS);
            assertThat(ex.getDetails()).containsEntry("orderCode", oldCode).containsEntry("amountVnd", 50_000L);
        });
        assertThat(statusOf(oldCode)).isEqualTo("PENDING");
        assertThat(orderCountOf(userId)).isEqualTo(1);
    }

    @Test
    void createTopUpOrder_withReplacePending_cancelsOldAndCreatesNewInOneStep() {
        // given (c)
        long userId = insertUser();
        String oldCode = paymentService.createTopUpOrder(userId, 50_000).orderCode();

        // when: "Hủy đơn cũ & tạo đơn mới"
        var created = paymentService.createTopUpOrder(userId, 20_000, true);

        // then: đơn cũ CANCELLED có lý do, đơn mới PENDING, chỉ còn đúng một đơn đang chờ; ví không đổi
        assertThat(statusOf(oldCode)).isEqualTo("CANCELLED");
        assertThat(paymentService.getOwnOrder(userId, oldCode).failureReason())
                .isEqualTo("Bạn đã hủy đơn này để tạo đơn nạp mới.");
        assertThat(statusOf(created.orderCode())).isEqualTo("PENDING");
        var pending = paymentService.listOwnOrders(userId, PaymentOrderStatus.PENDING, 0, 10);
        assertThat(pending.items()).extracting("orderCode").containsExactly(created.orderCode());
        assertThat(balanceOf(userId)).isZero();
    }

    @Test
    void expiredPendingOrder_movesToHistoryAsExpiredAndAllowsNewOrder() {
        // given (d): đơn đã qua hạn cộng dung sai, job chưa chạy
        long userId = insertUser();
        String oldCode = paymentService.createTopUpOrder(userId, 20_000).orderCode();
        makeOlderThanDeadline(oldCode, GRACE_MINUTES + 1);

        // when
        var pending = paymentService.listOwnOrders(userId, PaymentOrderStatus.PENDING, 0, 10);
        var history = paymentService.listOwnOrders(userId, null, 0, 10);

        // then: không còn trong mục đang chờ, lịch sử ghi EXPIRED, không có URL thanh toán; tạo đơn mới được
        assertThat(pending.items()).isEmpty();
        assertThat(history.items()).hasSize(1);
        assertThat(history.items().get(0).status()).isEqualTo(PaymentOrderStatus.EXPIRED);
        assertThat(history.items().get(0).paymentUrl()).isNull();
        assertThat(paymentService.createTopUpOrder(userId, 20_000).status()).isEqualTo(PaymentOrderStatus.PENDING);
    }

    @Test
    void pendingOrderPastExpiresAtButWithinGrace_isNotListedAsWaitingAndDoesNotBlockNewOrder() {
        // given: đơn đã qua hạn thanh toán 1 phút, còn trong dung sai chờ IPN
        long userId = insertUser();
        String oldCode = paymentService.createTopUpOrder(userId, 20_000).orderCode();
        makeOlderThanDeadline(oldCode, 1);

        // when + then: không hiện như đang chờ, không có URL thanh toán, không chặn đơn mới
        assertThat(paymentService
                        .listOwnOrders(userId, PaymentOrderStatus.PENDING, 0, 10)
                        .items())
                .isEmpty();
        assertThat(paymentService.getOwnOrder(userId, oldCode).paymentUrl()).isNull();
        assertThat(paymentService.createTopUpOrder(userId, 20_000).status()).isEqualTo(PaymentOrderStatus.PENDING);
    }

    @Test
    void listOwnOrders_neverReturnsOrdersOfOtherUsers() {
        // given (e): user A có đơn, user B không
        long userA = insertUser();
        long userB = insertUser();
        String codeOfA = paymentService.createTopUpOrder(userA, 20_000).orderCode();

        // when + then: B không thấy đơn của A ở cả mục đang chờ, lịch sử, xem chi tiết hay hủy
        assertThat(paymentService.listOwnOrders(userB, null, 0, 10).items()).isEmpty();
        assertThat(paymentService
                        .listOwnOrders(userB, PaymentOrderStatus.PENDING, 0, 10)
                        .items())
                .isEmpty();
        assertThat(catchThrowable(() -> paymentService.getOwnOrder(userB, codeOfA)))
                .isInstanceOf(BusinessException.class);
        assertThat(catchThrowable(() -> paymentService.cancelOwnOrder(userB, codeOfA)))
                .isInstanceOf(BusinessException.class);
        assertThat(statusOf(codeOfA)).isEqualTo("PENDING");
    }

    @Test
    void listOwnOrders_historyIsNewestFirstAndPaged() {
        // given: 3 đơn đã kết thúc (tạo rồi hủy lần lượt)
        long userId = insertUser();
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            String code = paymentService.createTopUpOrder(userId, 20_000).orderCode();
            paymentService.cancelOwnOrder(userId, code);
            jdbc.update(
                    "UPDATE payment_orders SET created_at = now() - (? * interval '1 minute') WHERE order_code = ?",
                    3 - i,
                    code);
            codes.add(code);
        }

        // when
        var firstPage = paymentService.listOwnOrders(userId, null, 0, 2);
        var secondPage = paymentService.listOwnOrders(userId, null, 1, 2);

        // then
        assertThat(firstPage.totalElements()).isEqualTo(3);
        assertThat(firstPage.items()).extracting("orderCode").containsExactly(codes.get(2), codes.get(1));
        assertThat(secondPage.items()).extracting("orderCode").containsExactly(codes.get(0));
    }

    private void makeOlderThanDeadline(String orderCode, long extraMinutes) {
        long expireMinutes = configService.getInt(ConfigKey.PAYMENT_ORDER_EXPIRE_MINUTES);
        jdbc.update(
                "UPDATE payment_orders SET created_at = ? WHERE order_code = ?",
                Timestamp.from(Instant.now().minus(expireMinutes + extraMinutes, ChronoUnit.MINUTES)),
                orderCode);
    }

    private String statusOf(String orderCode) {
        return jdbc.queryForObject("SELECT status FROM payment_orders WHERE order_code = ?", String.class, orderCode);
    }

    private long orderCountOf(long userId) {
        return jdbc.queryForObject("SELECT count(*) FROM payment_orders WHERE user_id = ?", Long.class, userId);
    }

    // ---------- hàm hỗ trợ ----------

    private long insertUser() {
        // Email và phone có unique index nên mỗi user một giá trị riêng; đầu số khác CreditServiceIT để không trùng.
        int sequence = PHONE_SEQUENCE.incrementAndGet();
        String email = "payment-it-" + sequence + "-" + System.nanoTime() + "@test.local";
        String phone = String.format("0988%06d", sequence);
        Long userId = jdbc.queryForObject(
                "INSERT INTO users (email, password_hash, full_name, phone, status)"
                        + " VALUES (?, 'not-a-real-hash', 'Payment IT', ?, 'ACTIVE') RETURNING id",
                Long.class,
                email,
                phone);
        createdUserIds.add(userId);
        return userId;
    }

    private long balanceOf(long userId) {
        return jdbc.queryForObject("SELECT credit_balance FROM users WHERE id = ?", Long.class, userId);
    }

    private long ledgerCountOf(long userId) {
        return jdbc.queryForObject("SELECT count(*) FROM credit_ledger WHERE user_id = ?", Long.class, userId);
    }
}
