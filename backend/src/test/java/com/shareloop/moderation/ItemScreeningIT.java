package com.shareloop.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shareloop.integration.ai.AiClient;
import com.shareloop.integration.ai.AiResponse;
import com.shareloop.integration.ai.AiScreeningContent;
import com.shareloop.moderation.event.ItemScreeningRequestedEvent;
import com.shareloop.moderation.service.ItemScreeningService;
import com.shareloop.support.IntegrationTest;
import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class ItemScreeningIT extends IntegrationTest {
    private static final String MODEL = "google/gemma-4-31b-it:free";
    private static final String LOW = """
            {"contactInfo":{"found":false,"evidence":null},
             "prohibited":{"found":false,"category":null,"evidence":null},
             "categoryMismatch":false,"swapIntentInGivePost":false,"qualityIssue":null,
             "risk":"low","reasonVi":"Không phát hiện vấn đề"}
            """;
    private static final String HIGH = """
            {"contactInfo":{"found":true,"evidence":"Zalo"},
             "prohibited":{"found":false,"category":null,"evidence":null},
             "categoryMismatch":false,"swapIntentInGivePost":false,"qualityIssue":null,
             "risk":"high","reasonVi":"Có thông tin liên hệ"}
            """;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    ItemScreeningService screeningService;

    @MockitoBean
    AiClient aiClient;

    private Long userId;
    private Long itemId;
    private Long categoryId;

    @BeforeEach
    void createItem() {
        when(aiClient.isAvailable()).thenReturn(true);
        userId = jdbc.sql("INSERT INTO users (email, password_hash, full_name, phone, status) "
                        + "VALUES (:email, 'unused', 'Screening Test', :phone, 'ACTIVE') RETURNING id")
                .param("email", "screen-" + UUID.randomUUID() + "@shareloop.local")
                .param(
                        "phone",
                        "09" + String.format("%08d", Math.abs(UUID.randomUUID().hashCode()) % 100000000))
                .query(Long.class)
                .single();
        categoryId = jdbc.sql("SELECT id FROM item_categories WHERE is_active ORDER BY id LIMIT 1")
                .query(Long.class)
                .single();
        long areaId = jdbc.sql("SELECT id FROM areas WHERE level = 2 ORDER BY id LIMIT 1")
                .query(Long.class)
                .single();
        itemId = jdbc.sql("INSERT INTO items (donor_id, category_id, area_id, offer_type, title, description, "
                        + "condition) VALUES (:userId, :categoryId, :areaId, 'GIVE', 'Sách', 'Sách còn tốt', 'GOOD') "
                        + "RETURNING id")
                .param("userId", userId)
                .param("categoryId", categoryId)
                .param("areaId", areaId)
                .query(Long.class)
                .single();
    }

    @AfterEach
    void cleanUp() {
        jdbc.sql("DELETE FROM activity_logs WHERE target_type = 'ITEM' AND target_id = :itemId")
                .param("itemId", itemId)
                .update();
        jdbc.sql("DELETE FROM items WHERE id = :itemId").param("itemId", itemId).update();
        jdbc.sql("DELETE FROM users WHERE id = :userId").param("userId", userId).update();
    }

    @Test
    void highRiskFlagsPendingItemAndLogsCallWithoutAccountData() throws Exception {
        when(aiClient.screen(any(), any())).thenReturn(new AiResponse(MODEL, HIGH));

        screeningService.screen(event());

        assertThat(value("ai_risk")).isEqualTo("HIGH");
        assertThat(value("status")).isEqualTo("PENDING_REVIEW");
        assertThat(value("ai_result ->> 'flag'")).isEqualTo("REVIEW");
        assertThat(value("ai_result ->> 'reason'")).isEqualTo("Có thông tin liên hệ");
        assertThat(value("ai_result ->> 'model'")).isEqualTo(MODEL);
        assertThat(jdbc.sql("SELECT count(*) FROM activity_logs WHERE type = 'AI_USAGE' AND target_id = :itemId")
                        .param("itemId", itemId)
                        .query(Integer.class)
                        .single())
                .isEqualTo(1);
        ArgumentCaptor<AiScreeningContent> payload = ArgumentCaptor.forClass(AiScreeningContent.class);
        ArgumentCaptor<Duration> timeout = ArgumentCaptor.forClass(Duration.class);
        verify(aiClient).screen(payload.capture(), timeout.capture());
        assertThat(payload.getValue().title()).isEqualTo("Sách");
        assertThat(payload.getValue().toString()).doesNotContain("screen-");
        assertThat(timeout.getValue()).isPositive().isLessThanOrEqualTo(Duration.ofSeconds(15));
    }

    @Test
    void providerFailureRetriesAndLeavesItemUnchecked() throws Exception {
        when(aiClient.screen(any(), any())).thenThrow(new IOException("provider unavailable"));

        screeningService.screen(event());

        assertThat(value("ai_risk")).isEqualTo("UNCHECKED");
        assertThat(value("status")).isEqualTo("PENDING_REVIEW");
        verify(aiClient, times(2)).screen(any(), any());
        assertThat(jdbc.sql("SELECT count(*) FROM activity_logs WHERE type = 'AI_USAGE' AND target_id = :itemId")
                        .param("itemId", itemId)
                        .query(Integer.class)
                        .single())
                .isEqualTo(2);
    }

    @Test
    void invalidJsonLeavesItemUnchecked() throws Exception {
        when(aiClient.screen(any(), any())).thenReturn(new AiResponse(MODEL, "{\"risk\":\"high\"}"));

        screeningService.screen(event());

        assertThat(value("ai_risk")).isEqualTo("UNCHECKED");
        verify(aiClient, times(2)).screen(any(), any());
    }

    @Test
    void resultFromEarlierEditCannotOverwriteNewContent() throws Exception {
        when(aiClient.screen(any(), any())).thenAnswer(invocation -> {
            jdbc.sql("UPDATE items SET title = 'New title' WHERE id = :itemId")
                    .param("itemId", itemId)
                    .update();
            return new AiResponse(MODEL, HIGH);
        });

        screeningService.screen(event());

        assertThat(value("ai_risk")).isEqualTo("UNCHECKED");
        assertThat(value("title")).isEqualTo("New title");
    }

    @Test
    void lowRiskResultPreservesTierOneReviewFlag() throws Exception {
        jdbc.sql("UPDATE items SET ai_result = '{\"flag\":\"REVIEW\",\"reason\":\"LEXICON_REVIEW\"}'::jsonb "
                        + "WHERE id = :itemId")
                .param("itemId", itemId)
                .update();
        when(aiClient.screen(any(), any())).thenReturn(new AiResponse(MODEL, LOW));

        screeningService.screen(event());

        assertThat(value("ai_risk")).isEqualTo("LOW");
        assertThat(value("ai_result ->> 'flag'")).isEqualTo("REVIEW");
        assertThat(value("ai_result ->> 'reason'")).isEqualTo("LEXICON_REVIEW");
    }

    private ItemScreeningRequestedEvent event() {
        return new ItemScreeningRequestedEvent(
                itemId, userId, categoryId, "GIVE", "Sách", "Sách còn tốt", null, "GOOD");
    }

    private String value(String column) {
        return jdbc.sql("SELECT " + column + " FROM items WHERE id = :itemId")
                .param("itemId", itemId)
                .query(String.class)
                .single();
    }
}
