package com.shareloop.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.shareloop.integration.payment.VnPaySigner;
import com.shareloop.support.IntegrationTest;
import com.shareloop.wallet.entity.PaymentOrder;
import com.shareloop.wallet.entity.PaymentProvider;
import com.shareloop.wallet.repository.PaymentOrderRepository;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
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

    @Test
    void transactionStatusMustAlsoBeSuccessful() throws Exception {
        ipn(signed("2000000", "00", "02"), "00");
        assertStatus("FAILED");
    }

    @Test
    void missingOrder_returns01() throws Exception {
        var params = signed("2000000", "00", "00");
        params.put("vnp_TxnRef", "missing-order");
        params.put("vnp_SecureHash", VnPaySigner.sign(params, "test-secret"));
        ipn(params, "01");
    }

    @Test
    void invalidSignature_doesNotChangeOrder() throws Exception {
        var params = signed("2000000", "00", "00");
        params.put("vnp_SecureHash", "0".repeat(128));
        ipn(params, "97");
        assertThat(jdbc.queryForObject(
                        "SELECT status = 'PENDING' AND NOT signature_verified AND callback_data IS NULL FROM payment_orders WHERE order_code = ?",
                        Boolean.class,
                        orderCode))
                .isTrue();
    }

    @Test
    void invalidAmount_includingFractionalVnd_returns04() throws Exception {
        ipn(signed("2000001", "00", "00"), "04");
        assertStatus("PENDING");
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
        assertThat(jdbc.queryForObject("SELECT credit_balance FROM users WHERE id = ?", Long.class, userId))
                .isZero();
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
        params.put("vnp_SecureHash", VnPaySigner.sign(params, "test-secret"));
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
