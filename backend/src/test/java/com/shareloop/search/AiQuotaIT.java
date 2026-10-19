package com.shareloop.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shareloop.common.exception.BusinessException;
import com.shareloop.search.service.AiQuotaService;
import com.shareloop.search.service.AiQuotaService.Reservation;
import com.shareloop.support.IntegrationTest;
import com.shareloop.wallet.WalletErrorCode;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

class AiQuotaIT extends IntegrationTest {
    @Autowired
    JdbcClient jdbc;

    @Autowired
    AiQuotaService quotaService;

    private long userId;

    @BeforeEach
    void setUp() {
        userId = jdbc.sql("INSERT INTO users (email, password_hash, full_name, phone, status) "
                        + "VALUES (:email, 'unused', 'Quota Test', :phone, 'ACTIVE') RETURNING id")
                .param("email", "quota-" + UUID.randomUUID() + "@shareloop.local")
                .param(
                        "phone",
                        "09" + String.format("%08d", Math.abs(UUID.randomUUID().hashCode()) % 100000000))
                .query(Long.class)
                .single();
    }

    @AfterEach
    void cleanUp() {
        jdbc.sql("DELETE FROM credit_ledger WHERE user_id = :id")
                .param("id", userId)
                .update();
        jdbc.sql("DELETE FROM users WHERE id = :id").param("id", userId).update();
    }

    @Test
    void trialIsOnceUntilTopUpAndRefundRestoresIt() {
        assertThat(quotaService.getQuota(userId).freeUsesRemaining()).isEqualTo(1);
        Reservation trial = quotaService.reserve(userId);
        assertThat(trial.paid()).isFalse();
        assertThat(quotaService.getQuota(userId).freeUsesRemaining()).isZero();
        quotaService.refund(userId, trial);
        assertThat(quotaService.getQuota(userId).freeUsesRemaining()).isEqualTo(1);
        quotaService.reserve(userId);
        assertThat(quotaService.getQuota(userId).freeUsesRemaining()).isZero();
    }

    @Test
    void toppedUpUserGetsFivePerDayThenPaysAndRefundsExactCharge() {
        jdbc.sql("UPDATE users SET has_topped_up = true, credit_balance = 10 WHERE id = :id")
                .param("id", userId)
                .update();
        assertThat(quotaService.getQuota(userId).freeUsesRemaining()).isEqualTo(5);
        for (int i = 0; i < 5; i++) {
            assertThat(quotaService.reserve(userId).paid()).isFalse();
        }
        assertThat(quotaService.getQuota(userId).freeUsesRemaining()).isZero();
        Reservation paid = quotaService.reserve(userId);
        assertThat(paid.paid()).isTrue();
        assertThat(jdbc.sql("SELECT credit_balance FROM users WHERE id = :id")
                        .param("id", userId)
                        .query(Long.class)
                        .single())
                .isEqualTo(8);
        quotaService.refund(userId, paid);
        assertThat(jdbc.sql("SELECT credit_balance FROM users WHERE id = :id")
                        .param("id", userId)
                        .query(Long.class)
                        .single())
                .isEqualTo(10);
        assertThat(jdbc.sql("SELECT count(*) FROM credit_ledger WHERE user_id = :id AND type = 'AI_SEARCH_FEE'")
                        .param("id", userId)
                        .query(Long.class)
                        .single())
                .isEqualTo(2);
    }

    @Test
    void oldDailyUsageResetsAndInsufficientBalanceNeverCallsAi() {
        jdbc.sql("UPDATE users SET has_topped_up = true, free_ai_used = 5, "
                        + "free_ai_date = CURRENT_DATE - 1, credit_balance = 0 WHERE id = :id")
                .param("id", userId)
                .update();
        assertThat(quotaService.getQuota(userId).freeUsesRemaining()).isEqualTo(5);
        for (int i = 0; i < 5; i++) {
            quotaService.reserve(userId);
        }
        assertThatThrownBy(() -> quotaService.reserve(userId))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(WalletErrorCode.WALLET_INSUFFICIENT_BALANCE));
        assertThat(quotaService.getQuota(userId).freeUsesRemaining()).isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM credit_ledger WHERE user_id = :id")
                        .param("id", userId)
                        .query(Long.class)
                        .single())
                .isZero();
    }
}
