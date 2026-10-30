package com.shareloop.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.shareloop.common.exception.BusinessException;
import com.shareloop.integration.payment.VnPayTestData;
import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.service.ConfigService;
import com.shareloop.support.IntegrationTest;
import com.shareloop.wallet.entity.PaymentOrder;
import com.shareloop.wallet.entity.PaymentProvider;
import com.shareloop.wallet.repository.PaymentOrderRepository;
import com.shareloop.wallet.service.CreditService;
import com.shareloop.wallet.service.ManualTopUpService;
import com.shareloop.wallet.service.PaymentService;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.LinkedMultiValueMap;

@AutoConfigureMockMvc
@TestPropertySource(properties = "app.payment.vnpay.hash-secret=test-secret")
class VnPayCallbackIT extends IntegrationTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PaymentOrderRepository orders;

    @Autowired
    PaymentService payment;

    @Autowired
    ManualTopUpService manual;

    @Autowired
    ConfigService configService;

    @MockitoSpyBean
    CreditService credit;

    long userId;
    String orderCode;

    @BeforeEach
    void setUp() {
        orderCode = UUID.randomUUID().toString().replace("-", "");
        userId = jdbc.queryForObject(
                "INSERT INTO users (email, password_hash, full_name, phone, status) VALUES (?, 'test-hash', 'VNPAY Test', ?, 'ACTIVE') RETURNING id",
                Long.class,
                orderCode + "@test.local",
                "09" + orderCode.substring(0, 8));
        orders.save(new PaymentOrder(userId, orderCode, 20000, 20, 1000, PaymentProvider.VNPAY));
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM credit_ledger WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM payment_orders WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM users WHERE id = ?", userId);
    }

    @Test
    void successfulIpn_creditsOnceAndPersistsGatewayData() throws Exception {
        var params = signed("2000000", "00", "00");
        ipn(params, "00");
        ipn(params, "02");
        assertThat(jdbc.queryForObject("SELECT credit_balance FROM users WHERE id = ?", Long.class, userId))
                .isEqualTo(20);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM credit_ledger WHERE user_id = ?", Long.class, userId))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT signature_verified AND provider_txn_id = '123456' AND callback_data ->> 'vnp_TransactionNo' = '123456' AND paid_at IS NOT NULL FROM payment_orders WHERE order_code = ?",
                        Boolean.class,
                        orderCode))
                .isTrue();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM credit_ledger l JOIN payment_orders p ON p.id = l.payment_order_id WHERE p.order_code = ? AND l.type = 'TOP_UP' AND l.amount = p.credits AND l.balance_after = p.credits",
                        Long.class,
                        orderCode))
                .isEqualTo(1);
        assertThat(credit.getWallet(userId).hasToppedUp()).isTrue();
    }

    @Test
    void officialCallbackFixture_reSignedWithTestKey_creditsSnapshot() throws Exception {
        jdbc.update(
                "UPDATE payment_orders SET order_code = '166117', amount_vnd = 10000, credits = 10 WHERE order_code = ?",
                orderCode);
        orderCode = "166117";
        ipn(VnPayTestData.officialCallback(), "00");
        assertStatus("SUCCESS");
        assertThat(credit.getWallet(userId).creditBalance()).isEqualTo(10);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM credit_ledger WHERE user_id = ? AND type = 'TOP_UP' AND amount = 10",
                        Long.class,
                        userId))
                .isEqualTo(1);
    }

    @Test
    void concurrentIpn_creditsOnce() throws Exception {
        var params = signed("2000000", "00", "00");
        var ready = new java.util.concurrent.CountDownLatch(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<String> task = () -> {
                ready.countDown();
                if (!start.await(10, java.util.concurrent.TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting for concurrent IPN");
                }
                var query = new LinkedMultiValueMap<String, String>();
                params.forEach(query::add);
                return mvc.perform(get("/api/v1/payments/vnpay/ipn").params(query))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
            };
            var first = executor.submit(task);
            var second = executor.submit(task);
            boolean bothReady = ready.await(10, java.util.concurrent.TimeUnit.SECONDS);
            start.countDown();
            assertThat(bothReady).isTrue();
            assertThat(java.util.List.of(
                            first.get(30, java.util.concurrent.TimeUnit.SECONDS),
                            second.get(30, java.util.concurrent.TimeUnit.SECONDS)))
                    .anySatisfy(body -> assertThat(body).contains("\"RspCode\":\"00\""))
                    .anySatisfy(body -> assertThat(body).contains("\"RspCode\":\"02\""));
        } finally {
            start.countDown();
        }
        assertThat(jdbc.queryForObject("SELECT credit_balance FROM users WHERE id = ?", Long.class, userId))
                .isEqualTo(20);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM credit_ledger WHERE user_id = ?", Long.class, userId))
                .isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({"00,02", "24,00", "24,02"})
    void bothResponseAndTransactionStatusMustBeSuccessful(String response, String transactionStatus) throws Exception {
        ipn(signed("2000000", response, transactionStatus), "00");
        assertStatus("FAILED");
        assertNoCredits();
    }

    @Test
    void missingOrder_returns01() throws Exception {
        var params = signed("2000000", "00", "00");
        params.put("vnp_TxnRef", "missing-order");
        VnPayTestData.signCallback(params);
        ipn(params, "01");
        assertPristineOrder();
        assertNoCredits();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(
            strings = {
                "bad",
                "gg",
                "00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000"
            })
    void invalidSignature_doesNotChangeOrderWalletOrLedger(String hash) throws Exception {
        var params = signed("2000000", "00", "00");
        if (hash == null) params.remove("vnp_SecureHash");
        else params.put("vnp_SecureHash", hash);
        ipn(params, "97");
        assertPristineOrder();
        assertNoCredits();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(
            strings = {
                "2000001",
                "20000",
                "abc",
                "-2000000",
                "2000000.0",
                " 2000000",
                "999999999999999999999999999999999"
            })
    void invalidAmount_includingMissingMalformedAndFractionalVnd_returns04(String amount) throws Exception {
        var params = signed("2000000", "00", "00");
        if (amount == null) params.remove("vnp_Amount");
        else params.put("vnp_Amount", amount);
        VnPayTestData.signCallback(params);
        ipn(params, "04");
        assertStatus("PENDING");
        assertNoCredits();
        assertThat(jdbc.queryForObject(
                        "SELECT signature_verified AND callback_data IS NOT NULL FROM payment_orders WHERE order_code = ?",
                        Boolean.class,
                        orderCode))
                .isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"MOCK", "MANUAL"})
    void wrongProvider_returns01WithoutChangingOrder(String provider) throws Exception {
        jdbc.update("UPDATE payment_orders SET provider = ? WHERE order_code = ?", provider, orderCode);
        ipn(signed("2000000", "00", "00"), "01");
        assertPristineOrder();
        assertNoCredits();
    }

    @ParameterizedTest
    @ValueSource(strings = {"FAILED", "EXPIRED"})
    void terminalUnpaidOrder_cannotBeCredited(String terminalStatus) throws Exception {
        jdbc.update("UPDATE payment_orders SET status = ? WHERE order_code = ?", terminalStatus, orderCode);
        ipn(signed("2000000", "00", "00"), "02");
        assertStatus(terminalStatus);
        assertNoCredits();
    }

    @Test
    void failureAfterActualCreditWrites_rollsBackOrderWalletLedgerAndCallback() throws Exception {
        doAnswer(invocation -> {
                    invocation.callRealMethod();
                    // Flush actual writes before failing, so rollback is checked against the database.
                    orders.flush();
                    throw new IllegalStateException("Injected failure after gateway credit");
                })
                .when(credit)
                .topUpFromGateway(anyLong());
        ipn(signed("2000000", "00", "00"), "99");
        assertPristineOrder();
        assertNoCredits();
    }

    @Test
    void mockAndManualOperations_cannotConfirmVnpayOrder() {
        assertNotPayable(() -> payment.confirmMockPayment(userId, orderCode));
        // Provider hiện tại không phải manual nên thao tác thủ công bị chặn ngay từ đầu (K2).
        assertBlocked(() -> manual.markPaid(userId, orderCode), WalletErrorCode.WALLET_MANUAL_TOPUP_DISABLED);
        assertBlocked(() -> manual.approve(userId, orderCode, null), WalletErrorCode.WALLET_MANUAL_TOPUP_DISABLED);
        assertBlocked(
                () -> manual.reject(userId, orderCode, "wrong provider"), WalletErrorCode.WALLET_MANUAL_TOPUP_DISABLED);
        assertPristineOrder();
        assertNoCredits();
    }

    private void assertNotPayable(org.assertj.core.api.ThrowableAssert.ThrowingCallable operation) {
        assertBlocked(operation, WalletErrorCode.WALLET_PAYMENT_ORDER_NOT_PAYABLE);
    }

    private void assertBlocked(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable operation, WalletErrorCode expected) {
        assertThatThrownBy(operation)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(expected));
    }

    @Test
    void unsuccessfulPayment_marksFailed() throws Exception {
        ipn(signed("2000000", "24", "02"), "00");
        assertStatus("FAILED");
        assertThat(jdbc.queryForObject("SELECT credit_balance FROM users WHERE id = ?", Long.class, userId))
                .isZero();
    }

    @Test
    void return_redirectsWithoutCrediting() throws Exception {
        var params = new LinkedMultiValueMap<String, String>();
        signed("2000000", "00", "00").forEach(params::add);
        mvc.perform(get("/api/v1/payments/vnpay/return").params(params))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("http://localhost:5173/credit?orderCode=" + orderCode + "&status=SUCCESS"));
        assertStatus("PENDING");
        assertPristineOrder();
        assertNoCredits();
    }

    @Test
    void invalidReturn_redirectsWithoutLeakingOrderCodeOrChangingState() throws Exception {
        var params = new LinkedMultiValueMap<String, String>();
        var data = signed("2000000", "00", "00");
        data.put("vnp_SecureHash", "bad");
        data.forEach(params::add);
        mvc.perform(get("/api/v1/payments/vnpay/return").params(params))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("http://localhost:5173/credit?orderCode=&status=INVALID_SIGNATURE"));
        assertPristineOrder();
        assertNoCredits();
    }

    @Test
    void ipn_whenPendingOrderPastDeadlineAndGrace_marksExpiredWithoutCredits() throws Exception {
        // given: đơn còn PENDING (job chưa chạy) nhưng đã quá hạn cả dung sai
        makeOrderOlderThanDeadlineBy(graceMinutes() + 1);

        // when, then: VNPAY báo thành công vẫn không được cộng
        ipn(signed("2000000", "00", "00"), "02");
        assertStatus("EXPIRED");
        assertNoCredits();
    }

    @Test
    void ipn_whenPendingOrderPastDeadlineButWithinGrace_creditsOnce() throws Exception {
        // given: quá hạn 1 phút nhưng còn trong dung sai đồng hồ
        makeOrderOlderThanDeadlineBy(Math.max(graceMinutes() - 1, 0));

        // when
        ipn(signed("2000000", "00", "00"), "00");

        // then
        assertStatus("SUCCESS");
        assertThat(credit.getWallet(userId).creditBalance()).isEqualTo(20);
    }

    @Test
    void ipn_whenOrderStillBeforeDeadline_creditsOnce() throws Exception {
        // given: đơn mới tạo ở setUp, còn xa hạn

        // when
        ipn(signed("2000000", "00", "00"), "00");

        // then
        assertStatus("SUCCESS");
        assertThat(credit.getWallet(userId).creditBalance()).isEqualTo(20);
    }

    private long graceMinutes() {
        return 2; // khớp app.payment.vnpay.expiry-grace-minutes trong application.yml
    }

    // Lùi created_at để đơn vượt hạn (payment_order_expire_minutes) đúng extraMinutes phút.
    private void makeOrderOlderThanDeadlineBy(long extraMinutes) {
        long ageMinutes = configService.getInt(ConfigKey.PAYMENT_ORDER_EXPIRE_MINUTES) + extraMinutes;
        jdbc.update(
                "UPDATE payment_orders SET created_at = ? WHERE order_code = ?",
                java.sql.Timestamp.from(
                        java.time.Instant.now().minus(ageMinutes, java.time.temporal.ChronoUnit.MINUTES)),
                orderCode);
    }

    private void assertNoCredits() {
        assertThat(credit.getWallet(userId).creditBalance()).isZero();
        assertThat(credit.getWallet(userId).hasToppedUp()).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM credit_ledger WHERE user_id = ?", Long.class, userId))
                .isZero();
    }

    private void assertPristineOrder() {
        assertThat(jdbc.queryForObject(
                        "SELECT status = 'PENDING' AND NOT signature_verified AND callback_data IS NULL AND provider_txn_id IS NULL AND paid_at IS NULL FROM payment_orders WHERE order_code = ?",
                        Boolean.class,
                        orderCode))
                .isTrue();
    }

    private Map<String, String> signed(String amount, String response, String status) {
        Map<String, String> params = new TreeMap<>(Map.of(
                "vnp_TxnRef",
                orderCode,
                "vnp_Amount",
                amount,
                "vnp_ResponseCode",
                response,
                "vnp_TransactionStatus",
                status,
                "vnp_TransactionNo",
                "123456"));
        VnPayTestData.signCallback(params);
        return params;
    }

    private void ipn(Map<String, String> params, String code) throws Exception {
        var query = new LinkedMultiValueMap<String, String>();
        params.forEach(query::add);
        mvc.perform(get("/api/v1/payments/vnpay/ipn").params(query))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode").value(code));
    }

    private void assertStatus(String status) {
        assertThat(jdbc.queryForObject(
                        "SELECT status FROM payment_orders WHERE order_code = ?", String.class, orderCode))
                .isEqualTo(status);
    }
}
