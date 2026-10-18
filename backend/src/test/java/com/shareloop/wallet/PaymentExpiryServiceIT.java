package com.shareloop.wallet;

import static org.assertj.core.api.Assertions.assertThat;

import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.service.ConfigService;
import com.shareloop.support.IntegrationTest;
import com.shareloop.wallet.service.PaymentExpiryService;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Test job hết hạn đơn nạp (FR-18) trên PostgreSQL thật. Gọi thẳng service với mốc {@code now} tự chọn (profile test
 * tắt app.jobs.enabled nên job không tự chạy). DB dùng chung với test khác nên chỉ kiểm các đơn do test này tạo, không
 * kiểm tổng số đơn của cả lượt.
 */
class PaymentExpiryServiceIT extends IntegrationTest {

    private static final AtomicInteger PHONE_SEQUENCE = new AtomicInteger();

    @Autowired
    PaymentExpiryService paymentExpiryService;

    @Autowired
    ConfigService configService;

    @Autowired
    JdbcTemplate jdbc;

    private final List<Long> createdUserIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (Long userId : createdUserIds) {
            jdbc.update("DELETE FROM payment_orders WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM users WHERE id = ?", userId);
        }
        createdUserIds.clear();
    }

    @Test
    void expireStaleOrders_whenPendingOlderThanExpireMinutes_marksExpired() {
        // given: đơn tạo trước hạn 1 phút
        Instant now = Instant.now();
        Instant createdAt = now.minus(expireMinutes() + 1, ChronoUnit.MINUTES);
        long orderId = insertPaymentOrder(insertUser(), "PENDING", createdAt);

        // when
        int expiredCount = paymentExpiryService.expireStaleOrders(now);

        // then
        assertThat(statusOf(orderId)).isEqualTo("EXPIRED");
        assertThat(expiredCount).isGreaterThanOrEqualTo(1);
    }

    @Test
    void expireStaleOrders_whenPendingStillWithinExpireMinutes_keepsPending() {
        // given: đơn còn 1 phút nữa mới tới hạn
        Instant now = Instant.now();
        Instant createdAt = now.minus(expireMinutes() - 1, ChronoUnit.MINUTES);
        long orderId = insertPaymentOrder(insertUser(), "PENDING", createdAt);

        // when
        paymentExpiryService.expireStaleOrders(now);

        // then
        assertThat(statusOf(orderId)).isEqualTo("PENDING");
    }

    // ---------- hàm hỗ trợ ----------

    private long expireMinutes() {
        return configService.getInt(ConfigKey.PAYMENT_ORDER_EXPIRE_MINUTES);
    }

    private long insertUser() {
        // Email và phone có unique index nên mỗi user một giá trị riêng; đầu số 0987 khác các test khác.
        int sequence = PHONE_SEQUENCE.incrementAndGet();
        String email = "payment-expiry-it-" + sequence + "-" + System.nanoTime() + "@test.local";
        String phone = String.format("0987%06d", sequence);
        Long userId = jdbc.queryForObject(
                "INSERT INTO users (email, password_hash, full_name, phone, status)"
                        + " VALUES (?, 'not-a-real-hash', 'Payment Expiry IT', ?, 'ACTIVE') RETURNING id",
                Long.class,
                email,
                phone);
        createdUserIds.add(userId);
        return userId;
    }

    // Ghi thẳng created_at chọn trước để kiểm mốc hết hạn; 20 Credit x 1.000 thoả CHECK amount_vnd >= 10000.
    private long insertPaymentOrder(long userId, String status, Instant createdAt) {
        String orderCode = "it-" + UUID.randomUUID().toString().replace("-", "");
        return jdbc.queryForObject(
                "INSERT INTO payment_orders"
                        + " (user_id, order_code, amount_vnd, credits, vnd_per_credit, provider, status, created_at)"
                        + " VALUES (?, ?, 20000, 20, 1000, 'MOCK', ?, ?) RETURNING id",
                Long.class,
                userId,
                orderCode,
                status,
                Timestamp.from(createdAt));
    }

    private String statusOf(long orderId) {
        return jdbc.queryForObject("SELECT status FROM payment_orders WHERE id = ?", String.class, orderId);
    }
}
