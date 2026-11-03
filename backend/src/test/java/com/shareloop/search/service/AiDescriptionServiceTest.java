package com.shareloop.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shareloop.audit.service.ActivityLogQueryService;
import com.shareloop.audit.service.ActivityLogService;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.integration.ai.GeminiDescriptionClient;
import com.shareloop.search.SearchErrorCode;
import com.shareloop.search.dto.AiDescriptionResponse;
import com.shareloop.search.entity.AiQuotaAccount;
import com.shareloop.search.repository.AiQuotaAccountRepository;
import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.service.ConfigService;
import com.shareloop.setting.service.LexiconMatcher;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class AiDescriptionServiceTest {
    private final GeminiDescriptionClient client = mock(GeminiDescriptionClient.class);
    private final AiQuotaAccountRepository accountRepository = mock(AiQuotaAccountRepository.class);
    private final ActivityLogQueryService logQueryService = mock(ActivityLogQueryService.class);
    private final ActivityLogService logService = mock(ActivityLogService.class);
    private final ConfigService configService = mock(ConfigService.class);
    private final AiDescriptionService service = new AiDescriptionService(
            client,
            accountRepository,
            logQueryService,
            logService,
            configService,
            new LexiconMatcher(),
            new JsonMapper(),
            Clock.fixed(Instant.parse("2026-11-02T17:00:00Z"), ZoneOffset.UTC));

    @BeforeEach
    void setUp() {
        when(configService.lexicon()).thenReturn(List.of());
        when(configService.getInt(ConfigKey.AI_DESCRIBE_PER_DAY)).thenReturn(5);
        when(configService.getInt(ConfigKey.AI_DESCRIBE_TIMEOUT_SECONDS)).thenReturn(20);
        when(accountRepository.lockByUserId(7L)).thenReturn(Optional.of(mock(AiQuotaAccount.class)));
        when(client.isAvailable()).thenReturn(true);
        when(client.model()).thenReturn("gemini-3.5-flash-lite");
    }

    @Test
    void buildsSafeDraftFromTitleAndLeavesUnknownFactsForSeller() throws Exception {
        when(client.describe("Đồng hồ Casio màu đen", Duration.ofSeconds(20))).thenReturn("""
                        {"itemType":"Đồng hồ",
                         "brand":"Casio","color":"đen","material":"thép","conditionHint":"Như mới",
                         "accessories":"hộp","defects":"","itemCategory":"WATCH",
                         "detailsToConfirm":["Kích cỡ mặt","Chất liệu dây","Tình trạng hoạt động","Dung tích"],
                         "extra":"ignored"}
                        """);

        AiDescriptionResponse response = service.describe(7L, "Đồng hồ Casio màu đen");

        assertThat(response.description()).contains("Thương hiệu: Casio", "Màu: đen", "Kích cỡ mặt: …");
        assertThat(response.description()).doesNotContain("Dung tích", "Chất liệu: …", "Như mới", "hộp");
        assertThat(response.attributes().stream()
                        .filter(attribute -> attribute.name().equals("Tình trạng hoạt động"))
                        .findFirst()
                        .orElseThrow()
                        .requiresUserInput())
                .isTrue();
        assertThat(response.remainingUses()).isEqualTo(4);
        verify(logQueryService)
                .countAiDescriptions(7L, Instant.parse("2026-11-02T17:00:00Z"), Instant.parse("2026-11-03T17:00:00Z"));
        verify(logService).log(eq("AI_USAGE"), eq(7L), eq("AI_DESCRIBE"), eq(null), anyMap(), eq(null));
    }

    @Test
    void containerSuggestsCapacityButNotWatchDetails() throws Exception {
        when(client.describe("Bình nước LocknLock 1 lít", Duration.ofSeconds(20)))
                .thenReturn("""
                {"itemType":"Bình nước","brand":"LocknLock","color":"","material":"",
                 "conditionHint":"","accessories":"","defects":"","itemCategory":"CONTAINER",
                 "detailsToConfirm":["Dung tích","Kích thước","Chất liệu dây"]}
                """);

        AiDescriptionResponse response = service.describe(7L, "Bình nước LocknLock 1 lít");

        assertThat(response.description()).contains("Dung tích: …", "Thương hiệu: LocknLock");
        assertThat(response.description()).doesNotContain("Kích cỡ mặt", "Chất liệu dây");
    }

    @Test
    void rejectsTitleContainingContactBeforeProviderCall() throws Exception {
        assertThatThrownBy(() -> service.describe(7L, "Gọi 0912345678 để lấy đồng hồ"))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(SearchErrorCode.AI_DESCRIPTION_TITLE_INVALID));
        verify(client, never()).describe(anyString(), any());
    }

    @Test
    void limitStopsProviderCall() throws Exception {
        when(logQueryService.countAiDescriptions(eq(7L), any(), any())).thenReturn(5L);
        assertThatThrownBy(() -> service.describe(7L, "Đồng hồ"))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(SearchErrorCode.AI_DESCRIPTION_LIMIT_REACHED));
        verify(client, never()).describe(anyString(), any());
    }

    @Test
    void providerFailureDoesNotUseDailyAllowance() throws Exception {
        when(client.describe("Đồng hồ", Duration.ofSeconds(20))).thenThrow(new IOException("timeout"));
        AiDescriptionResponse response = service.describe(7L, "Đồng hồ");
        assertThat(response.description()).isEmpty();
        assertThat(response.remainingUses()).isEqualTo(5);
        verify(logService).log(eq("AI_USAGE"), eq(7L), eq("AI_DESCRIBE_ERROR"), eq(null), anyMap(), eq(null));
    }

    @Test
    void malformedProviderResponseDoesNotUseDailyAllowance() throws Exception {
        when(client.describe("Đồng hồ", Duration.ofSeconds(20))).thenReturn("{}");
        AiDescriptionResponse response = service.describe(7L, "Đồng hồ");
        assertThat(response.description()).isEmpty();
        assertThat(response.remainingUses()).isEqualTo(5);
        verify(logService).log(eq("AI_USAGE"), eq(7L), eq("AI_DESCRIBE_ERROR"), eq(null), anyMap(), eq(null));
    }

    @Test
    void unrelatedDetailsDoNotUseDailyAllowance() throws Exception {
        when(client.describe("Đồng hồ", Duration.ofSeconds(20))).thenReturn("""
                {"itemType":"Đồng hồ","brand":"","color":"","material":"",
                 "conditionHint":"","accessories":"","defects":"","itemCategory":"WATCH",
                 "detailsToConfirm":["Dung tích"]}
                """);

        AiDescriptionResponse response = service.describe(7L, "Đồng hồ");

        assertThat(response.description()).isEmpty();
        assertThat(response.remainingUses()).isEqualTo(5);
        verify(logService).log(eq("AI_USAGE"), eq(7L), eq("AI_DESCRIBE_ERROR"), eq(null), anyMap(), eq(null));
    }

    @Test
    void stripsContactInformationFromProviderFields() throws Exception {
        when(client.describe("Đồng hồ", Duration.ofSeconds(20))).thenReturn("""
                        {"itemType":"Đồng hồ","brand":"0912345678","color":"","material":"",
                         "conditionHint":"","accessories":"","defects":"","itemCategory":"WATCH",
                         "detailsToConfirm":["Kích cỡ mặt"]}
                        """);
        AiDescriptionResponse response = service.describe(7L, "Đồng hồ");
        assertThat(response.description()).doesNotContain("0912345678", "Thương hiệu:");
    }
}
