package com.shareloop.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.shareloop.audit.service.ActivityLogService;
import com.shareloop.catalog.service.AreaService;
import com.shareloop.catalog.service.CategoryService;
import com.shareloop.common.api.PageResponse;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.integration.ai.AiResponse;
import com.shareloop.integration.ai.AiSearchClient;
import com.shareloop.item.dto.ItemResponse;
import com.shareloop.item.service.ItemService;
import com.shareloop.search.dto.AiSearchResponse;
import com.shareloop.search.service.AiQuotaService;
import com.shareloop.search.service.AiSearchService;
import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.service.ConfigService;
import com.shareloop.support.IntegrationTest;
import com.shareloop.wallet.WalletErrorCode;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.json.JsonMapper;

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
    void trialIsConsumedOnlyWhenSearchHasResults() {
        assertThat(quotaService.getQuota(userId).freeUsesRemaining()).isEqualTo(1);
        quotaService.checkAvailable(userId);
        assertThat(quotaService.getQuota(userId).freeUsesRemaining()).isEqualTo(1);
        assertThat(quotaService.consume(userId)).isFalse();
        assertThat(quotaService.getQuota(userId).freeUsesRemaining()).isZero();
    }

    @Test
    void toppedUpUserGetsFivePerDayThenPaysOnce() {
        jdbc.sql("UPDATE users SET has_topped_up = true, credit_balance = 10 WHERE id = :id")
                .param("id", userId)
                .update();
        assertThat(quotaService.getQuota(userId).freeUsesRemaining()).isEqualTo(5);
        for (int i = 0; i < 5; i++) {
            assertThat(quotaService.consume(userId)).isFalse();
        }
        assertThat(quotaService.getQuota(userId).freeUsesRemaining()).isZero();
        quotaService.checkAvailable(userId);
        assertThat(quotaService.consume(userId)).isTrue();
        assertThat(jdbc.sql("SELECT credit_balance FROM users WHERE id = :id")
                        .param("id", userId)
                        .query(Long.class)
                        .single())
                .isEqualTo(8);
        assertThat(jdbc.sql("SELECT count(*) FROM credit_ledger WHERE user_id = :id AND type = 'AI_SEARCH_FEE'")
                        .param("id", userId)
                        .query(Long.class)
                        .single())
                .isEqualTo(1);
    }

    @Test
    void successfulAiSearchConsumesFiveFreeUsesThenChargesSixth() throws Exception {
        jdbc.sql("UPDATE users SET has_topped_up = true, credit_balance = 10 WHERE id = :id")
                .param("id", userId)
                .update();
        AiSearchClient aiClient = mock(AiSearchClient.class);
        CategoryService categoryService = mock(CategoryService.class);
        AreaService areaService = mock(AreaService.class);
        ItemService itemService = mock(ItemService.class);
        ConfigService configService = mock(ConfigService.class);
        when(aiClient.isAvailable()).thenReturn(true);
        when(aiClient.extract(eq("sách"), any(), eq(Duration.ofSeconds(15))))
                .thenReturn(
                        new AiResponse(
                                "cohere/north-mini-code:free",
                                "{\"keywords\":\"sách\",\"categoryId\":null,\"areaId\":null,\"condition\":null,\"offerType\":null}"));
        when(categoryService.listActive()).thenReturn(List.of());
        when(areaService.listTree()).thenReturn(List.of());
        when(configService.getInt(ConfigKey.AI_SEARCH_TIMEOUT_SECONDS)).thenReturn(15);
        when(configService.getInt(ConfigKey.AI_SEARCH_FEE)).thenReturn(2);
        when(itemService.searchApprovedForAi(null, null, "sách", null, null, 0, 20))
                .thenReturn(
                        new PageResponse<>(List.of(), 0, 20, 0),
                        new PageResponse<>(List.of(mock(ItemResponse.class)), 0, 20, 1));
        AiSearchService searchService = new AiSearchService(
                aiClient,
                categoryService,
                areaService,
                itemService,
                configService,
                quotaService,
                mock(ActivityLogService.class),
                new JsonMapper());

        AiSearchResponse empty = searchService.search(userId, "sách", 0, 20);
        assertThat(empty.results().totalElements()).isZero();
        assertThat(empty.quota().freeUsesRemaining()).isEqualTo(5);
        assertThat(jdbc.sql("SELECT count(*) FROM credit_ledger WHERE user_id = :id")
                        .param("id", userId)
                        .query(Long.class)
                        .single())
                .isZero();

        for (int remaining = 4; remaining >= 0; remaining--) {
            AiSearchResponse response = searchService.search(userId, "sách", 0, 20);
            assertThat(response.fallback()).isFalse();
            assertThat(response.quota().freeUsesRemaining()).isEqualTo(remaining);
            assertThat(response.chargedCredits()).isZero();
        }
        AiSearchResponse paid = searchService.search(userId, "sách", 0, 20);
        assertThat(paid.fallback()).isFalse();
        assertThat(paid.chargedCredits()).isEqualTo(2);
        assertThat(jdbc.sql("SELECT credit_balance FROM users WHERE id = :id")
                        .param("id", userId)
                        .query(Long.class)
                        .single())
                .isEqualTo(8);
    }

    @Test
    void oldDailyUsageResetsAndInsufficientBalanceNeverCallsAi() {
        jdbc.sql("UPDATE users SET has_topped_up = true, free_ai_used = 5, "
                        + "free_ai_date = CURRENT_DATE - 1, credit_balance = 0 WHERE id = :id")
                .param("id", userId)
                .update();
        assertThat(quotaService.getQuota(userId).freeUsesRemaining()).isEqualTo(5);
        for (int i = 0; i < 5; i++) {
            quotaService.consume(userId);
        }
        assertThatThrownBy(() -> quotaService.checkAvailable(userId))
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
