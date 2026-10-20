package com.shareloop.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

import com.shareloop.audit.service.ActivityLogService;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.service.ConfigService;
import com.shareloop.support.IntegrationTest;
import com.shareloop.wallet.dto.TopUpOrderDetailResponse;
import com.shareloop.wallet.entity.PaymentOrderStatus;
import com.shareloop.wallet.service.CreditService;
import com.shareloop.wallet.service.ManualTopUpService;
import com.shareloop.wallet.service.PaymentExpiryService;
import com.shareloop.wallet.service.PaymentService;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@TestPropertySource(properties = {"app.payment.provider=manual", "app.payment.mock.enabled=true"})
class ManualTopUpIT extends IntegrationTest {
    private static final AtomicInteger PHONE_SEQUENCE = new AtomicInteger();

    @Autowired
    ManualTopUpService manual;

    @Autowired
    PaymentService payment;

    @Autowired
    CreditService credit;

    @Autowired
    PaymentExpiryService expiry;

    @Autowired
    ConfigService config;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoSpyBean
    ActivityLogService audit;

    private final List<Long> users = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        reset(audit);
        for (long id : users) {
            jdbc.update(
                    "DELETE FROM activity_logs WHERE target_type = 'PAYMENT_ORDER' AND target_id IN (SELECT id FROM payment_orders WHERE user_id = ?)",
                    id);
            jdbc.update("DELETE FROM credit_ledger WHERE user_id = ?", id);
            jdbc.update("DELETE FROM payment_orders WHERE user_id = ?", id);
        }
        for (long id : users) jdbc.update("DELETE FROM users WHERE id = ?", id);
    }

    @Test
    void markPaidIsIdempotentAndNeverCreditsWallet() {
        long user = user();
        String code = order(user);
        TopUpOrderDetailResponse first = manual.markPaid(user, code);
        TopUpOrderDetailResponse second = manual.markPaid(user, code);
        assertThat(second.submittedAt()).isEqualTo(first.submittedAt());
        assertThat(second.status()).isEqualTo(PaymentOrderStatus.PENDING);
        assertThat(balance(user)).isZero();
        assertThat(ledger(user)).isZero();
        assertThat(credit.getWallet(user).hasToppedUp()).isFalse();
        assertThat(manual.list(PaymentOrderStatus.PENDING, true, 0, 20).items())
                .extracting(TopUpOrderDetailResponse::orderCode)
                .contains(code);
    }

    @Test
    void ownerChecksHideAnotherUsersOrder() {
        long owner = user();
        long other = user();
        String code = order(owner);
        assertThatThrownBy(() -> manual.getOrder(other, code))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(WalletErrorCode.WALLET_PAYMENT_ORDER_NOT_FOUND));
        assertThatThrownBy(() -> manual.markPaid(other, code))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(WalletErrorCode.WALLET_PAYMENT_ORDER_NOT_FOUND));
        assertThat(manual.getOrder(owner, code).submittedAt()).isNull();
    }

    @Test
    void repeatedAndConcurrentApprovalsCreditOnceAndAuditOnce() throws Exception {
        long user = user();
        long admin = user();
        String code = order(user);
        manual.markPaid(user, code);
        List<String> results = race(
                () -> manual.approve(admin, code, "checked").status().name(),
                () -> manual.approve(admin, code, "second").status().name());
        assertThat(results).containsOnly("SUCCESS");
        assertThat(manual.approve(admin, code, "third").reviewNote()).isIn("checked", "second");
        assertThat(balance(user)).isEqualTo(20);
        assertThat(ledger(user)).isEqualTo(1);
        assertThat(credit.getWallet(user).hasToppedUp()).isTrue();
        assertThat(auditCount(code)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT signature_verified FROM payment_orders WHERE order_code = ?", Boolean.class, code))
                .isFalse();
        assertThat(jdbc.queryForObject("SELECT balance_after FROM credit_ledger WHERE user_id = ?", Long.class, user))
                .isEqualTo(20);
    }

    @Test
    void approveAndRejectRaceHasExactlyOneTerminalDecision() throws Exception {
        long user = user();
        long admin = user();
        String code = order(user);
        manual.markPaid(user, code);
        List<String> results = race(
                () -> decision(() -> manual.approve(admin, code, "ok")),
                () -> decision(() -> manual.reject(admin, code, "no")));
        assertThat(results).contains("CONFLICT");
        String status = status(code);
        assertThat(status).isIn("SUCCESS", "FAILED");
        assertThat(balance(user)).isEqualTo(status.equals("SUCCESS") ? 20 : 0);
        assertThat(ledger(user)).isEqualTo(status.equals("SUCCESS") ? 1 : 0);
        assertThat(auditCount(code)).isEqualTo(1);
    }

    @Test
    void rejectsUnsubmittedAndWrongProviderOrders() {
        long user = user();
        long admin = user();
        String code = order(user);
        assertThatThrownBy(() -> manual.approve(admin, code, null)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> manual.reject(admin, code, "no")).isInstanceOf(BusinessException.class);
        manual.markPaid(user, code);
        jdbc.update("UPDATE payment_orders SET provider = 'MOCK' WHERE order_code = ?", code);
        assertThatThrownBy(() -> manual.approve(admin, code, null)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> manual.markPaid(user, code)).isInstanceOf(BusinessException.class);
        assertThat(balance(user)).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"FAILED", "EXPIRED"})
    void cannotApproveTerminalOrders(String terminalStatus) {
        long user = user();
        long admin = user();
        String code = order(user);
        manual.markPaid(user, code);
        jdbc.update("UPDATE payment_orders SET status = ? WHERE order_code = ?", terminalStatus, code);
        assertThatThrownBy(() -> manual.approve(admin, code, null)).isInstanceOf(BusinessException.class);
        assertThat(ledger(user)).isZero();
    }

    @Test
    void rejectionRequiresReasonAndNeverCredits() {
        long user = user();
        long admin = user();
        String code = order(user);
        manual.markPaid(user, code);
        assertThatThrownBy(() -> manual.reject(admin, code, " ")).isInstanceOf(BusinessException.class);
        TopUpOrderDetailResponse rejected = manual.reject(admin, code, "Không hợp lệ");
        assertThat(rejected.status()).isEqualTo(PaymentOrderStatus.FAILED);
        assertThat(rejected.reviewedBy()).isEqualTo(admin);
        assertThat(balance(user)).isZero();
        assertThat(ledger(user)).isZero();
        assertThat(auditCount(code)).isEqualTo(1);
    }

    @Test
    void expiryIsCheckedImmediatelyAndSubmittedOrdersSurviveJob() {
        long user = user();
        String submitted = order(user);
        manual.markPaid(user, submitted);
        String unsubmitted = order(user);
        String readExpired = order(user);
        Instant old =
                Instant.now().minus(config.getInt(ConfigKey.PAYMENT_ORDER_EXPIRE_MINUTES) + 1, ChronoUnit.MINUTES);
        jdbc.update("UPDATE payment_orders SET created_at = ? WHERE user_id = ?", Timestamp.from(old), user);
        assertThatThrownBy(() -> manual.markPaid(user, unsubmitted)).isInstanceOf(BusinessException.class);
        assertThat(status(unsubmitted)).isEqualTo("EXPIRED");
        assertThat(manual.getOrder(user, readExpired).status()).isEqualTo(PaymentOrderStatus.EXPIRED);
        expiry.expireStaleOrders(Instant.now());
        assertThat(status(submitted)).isEqualTo("PENDING");
        assertThat(manual.markPaid(user, submitted).submittedAt()).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"MOCK", "VNPAY"})
    void gatewayExpiryStillAppliesEvenWithSubmittedTimestamp(String provider) {
        long user = user();
        String code = order(user);
        manual.markPaid(user, code);
        jdbc.update(
                "UPDATE payment_orders SET provider = ?, created_at = ? WHERE order_code = ?",
                provider,
                Timestamp.from(Instant.now().minus(1, ChronoUnit.DAYS)),
                code);
        expiry.expireStaleOrders(Instant.now());
        assertThat(status(code)).isEqualTo("EXPIRED");
    }

    @Test
    void manualProviderBlocksMockConfirmIncludingOldMockOrders() {
        long user = user();
        String code = order(user);
        assertThatThrownBy(() -> payment.confirmMockPayment(user, code)).isInstanceOf(BusinessException.class);
        jdbc.update("UPDATE payment_orders SET provider = 'MOCK' WHERE order_code = ?", code);
        assertThatThrownBy(() -> payment.confirmMockPayment(user, code)).isInstanceOf(BusinessException.class);
        assertThat(balance(user)).isZero();
    }

    @Test
    void auditFailureRollsBackOrderWalletLedgerAndReview() {
        long user = user();
        long admin = user();
        String code = order(user);
        manual.markPaid(user, code);
        doAnswer(invocation -> {
                    invocation.callRealMethod();
                    throw new IllegalStateException("audit unavailable");
                })
                .when(audit)
                .log(eq("TOP_UP_APPROVED"), eq(admin), eq("PAYMENT_ORDER"), anyLong(), anyMap(), any());
        assertThatThrownBy(() -> manual.approve(admin, code, "ok")).isInstanceOf(IllegalStateException.class);
        assertThat(status(code)).isEqualTo("PENDING");
        assertThat(manual.getOrder(user, code).reviewedAt()).isNull();
        assertThat(balance(user)).isZero();
        assertThat(ledger(user)).isZero();
        assertThat(auditCount(code)).isZero();
        assertThat(credit.getWallet(user).hasToppedUp()).isFalse();
    }

    @Test
    void approvalUsesCreditSnapshotAfterConfigChanges() {
        long user = user();
        long admin = user();
        String code = order(user);
        manual.markPaid(user, code);
        String oldValue = jdbc.queryForObject(
                "SELECT attr_value FROM website_attributes WHERE attr_group = 'CONFIG' AND attr_key = 'vnd_per_credit'",
                String.class);
        try {
            jdbc.update(
                    "UPDATE website_attributes SET attr_value = '2000' WHERE attr_group = 'CONFIG' AND attr_key = 'vnd_per_credit'");
            assertThat(manual.approve(admin, code, null).credits()).isEqualTo(20);
            assertThat(balance(user)).isEqualTo(20);
        } finally {
            jdbc.update(
                    "UPDATE website_attributes SET attr_value = ? WHERE attr_group = 'CONFIG' AND attr_key = 'vnd_per_credit'",
                    oldValue);
        }
    }

    private long user() {
        long id = jdbc.queryForObject(
                "INSERT INTO users (email, password_hash, full_name, phone, status) VALUES (?, 'test-only', 'Manual IT', ?, 'ACTIVE') RETURNING id",
                Long.class,
                UUID.randomUUID() + "@manual.test",
                String.format("0976%06d", PHONE_SEQUENCE.incrementAndGet()));
        users.add(id);
        return id;
    }

    private String order(long user) {
        return payment.createTopUpOrder(user, 20_000).orderCode();
    }

    private long balance(long user) {
        return jdbc.queryForObject("SELECT credit_balance FROM users WHERE id = ?", Long.class, user);
    }

    private int ledger(long user) {
        return jdbc.queryForObject("SELECT count(*) FROM credit_ledger WHERE user_id = ?", Integer.class, user);
    }

    private String status(String code) {
        return jdbc.queryForObject("SELECT status FROM payment_orders WHERE order_code = ?", String.class, code);
    }

    private int auditCount(String code) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM activity_logs WHERE target_type = 'PAYMENT_ORDER' AND target_id = (SELECT id FROM payment_orders WHERE order_code = ?)",
                Integer.class,
                code);
    }

    private String decision(Callable<TopUpOrderDetailResponse> action) throws Exception {
        try {
            return action.call().status().name();
        } catch (BusinessException ex) {
            return "CONFLICT";
        }
    }

    private List<String> race(Callable<String> first, Callable<String> second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> {
                ready.countDown();
                start.await();
                return first.call();
            });
            var b = executor.submit(() -> {
                ready.countDown();
                start.await();
                return second.call();
            });
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS));
        }
    }
}
