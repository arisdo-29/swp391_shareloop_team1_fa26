package com.shareloop.wallet;

import static org.assertj.core.api.Assertions.assertThat;

import com.shareloop.support.IntegrationTest;
import com.shareloop.wallet.dto.TopUpConfirmResponse;
import com.shareloop.wallet.entity.PaymentOrderStatus;
import com.shareloop.wallet.service.PaymentService;
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
    JdbcTemplate jdbc;

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
