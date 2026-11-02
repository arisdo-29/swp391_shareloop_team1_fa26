package com.shareloop.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import com.shareloop.search.SearchErrorCode;
import com.shareloop.search.dto.AiQuotaResponse;
import com.shareloop.search.dto.AiSearchResponse;
import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.service.ConfigService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class AiSearchServiceTest {
    @Mock
    AiSearchClient aiClient;

    @Mock
    CategoryService categoryService;

    @Mock
    AreaService areaService;

    @Mock
    ItemService itemService;

    @Mock
    ConfigService configService;

    @Mock
    AiQuotaService quotaService;

    @Mock
    ActivityLogService activityLogService;

    private AiSearchService service;

    @BeforeEach
    void setUp() {
        service = new AiSearchService(
                aiClient,
                categoryService,
                areaService,
                itemService,
                configService,
                quotaService,
                activityLogService,
                new JsonMapper());
    }

    @Test
    void findsRealItemsForAnIndirectNeedAndChargesOnce() throws Exception {
        ready();
        ItemResponse accessory = item(11L);
        ItemResponse book = item(12L);
        when(itemService.searchApprovedForAi(null, null, "", null, null, 0, 100))
                .thenReturn(new PageResponse<>(List.of(accessory, book), 0, 100, 2));
        when(aiClient.recommend(eq("Muốn trông có phong cách hơn"), any(), eq(Duration.ofSeconds(15))))
                .thenReturn(new AiResponse(
                        "gemini-3.5-flash-lite",
                        "{\"inScope\":true,\"itemIds\":[11],\"message\":\"Có món hợp với bạn\"}"));
        when(itemService.getItem(11L, null)).thenReturn(accessory);
        when(quotaService.consume(7L)).thenReturn(true);
        when(configService.getInt(ConfigKey.AI_SEARCH_FEE)).thenReturn(2);

        AiSearchResponse result = service.search(7L, "Muốn trông có phong cách hơn");

        assertThat(result.results().items()).containsExactly(accessory);
        assertThat(result.message()).isEqualTo("Có món hợp với bạn");
        assertThat(result.chargedCredits()).isEqualTo(2);
        verify(quotaService).consume(7L);
        verify(activityLogService).log(eq("AI_USAGE"), eq(7L), eq("AI_SEARCH"), eq(null), anyMap(), eq(null));
    }

    @Test
    void rejectsInventedItemIdWithoutCharging() throws Exception {
        ready();
        ItemResponse available = item(11L);
        when(itemService.searchApprovedForAi(null, null, "", null, null, 0, 100))
                .thenReturn(new PageResponse<>(List.of(available), 0, 100, 1));
        when(aiClient.recommend(any(), any(), any()))
                .thenReturn(new AiResponse(
                        "gemini-3.5-flash-lite", "{\"inScope\":true,\"itemIds\":[999],\"message\":\"Có món\"}"));

        AiSearchResponse result = service.search(7L, "Tìm đồng hồ");

        assertThat(result.fallback()).isTrue();
        assertThat(result.results().items()).isEmpty();
        verify(quotaService, never()).consume(7L);
    }

    @Test
    void excludesItemThatWasReservedAfterModelSelection() throws Exception {
        ready();
        ItemResponse available = item(11L);
        when(itemService.searchApprovedForAi(null, null, "", null, null, 0, 100))
                .thenReturn(new PageResponse<>(List.of(available), 0, 100, 1));
        when(aiClient.recommend(any(), any(), any()))
                .thenReturn(new AiResponse(
                        "gemini-3.5-flash-lite", "{\"inScope\":true,\"itemIds\":[11],\"message\":\"Có món\"}"));
        when(itemService.getItem(11L, null)).thenThrow(new BusinessException(SearchErrorCode.SEARCH_QUERY_INVALID));

        AiSearchResponse result = service.search(7L, "Tìm đồng hồ");

        assertThat(result.results().items()).isEmpty();
        assertThat(result.chargedCredits()).isZero();
        verify(quotaService, never()).consume(7L);
    }

    @Test
    void refusesOffTopicRequestWithoutCharging() throws Exception {
        ready();
        when(itemService.searchApprovedForAi(null, null, "", null, null, 0, 100))
                .thenReturn(new PageResponse<>(List.of(), 0, 100, 0));
        when(aiClient.recommend(any(), any(), any()))
                .thenReturn(new AiResponse(
                        "gemini-3.5-flash-lite", "{\"inScope\":false,\"itemIds\":[],\"message\":\"Ngoài phạm vi\"}"));

        AiSearchResponse result = service.search(7L, "Thời tiết hôm nay?");

        assertThat(result.outOfScope()).isTrue();
        assertThat(result.results().items()).isEmpty();
        verify(quotaService, never()).consume(7L);
    }

    @Test
    void readsLaterDatabasePagesAndKeepsEarlierSuggestions() throws Exception {
        ready();
        List<ItemResponse> firstPage = new ArrayList<>();
        for (long id = 1; id <= 100; id++) {
            firstPage.add(item(id));
        }
        ItemResponse lastItem = item(101L);
        when(itemService.searchApprovedForAi(null, null, "", null, null, 0, 100))
                .thenReturn(new PageResponse<>(firstPage, 0, 100, 101));
        when(itemService.searchApprovedForAi(null, null, "", null, null, 1, 100))
                .thenReturn(new PageResponse<>(List.of(lastItem), 1, 100, 101));
        when(aiClient.recommend(any(), any(), any()))
                .thenReturn(
                        new AiResponse(
                                "gemini-3.5-flash-lite",
                                "{\"inScope\":true,\"itemIds\":[1],\"message\":\"Đã xem lô đầu\"}"),
                        new AiResponse(
                                "gemini-3.5-flash-lite",
                                "{\"inScope\":true,\"itemIds\":[101,1],\"message\":\"Có món phù hợp\"}"));
        when(itemService.getItem(101L, null)).thenReturn(lastItem);
        when(itemService.getItem(1L, null)).thenReturn(firstPage.get(0));

        AiSearchResponse result = service.search(7L, "Tìm đồ trang sức");

        assertThat(result.results().items()).containsExactly(lastItem, firstPage.get(0));
        verify(aiClient, org.mockito.Mockito.times(2)).recommend(any(), any(), any());
        verify(quotaService).consume(7L);
    }

    @Test
    void rejectsContactInfoBeforeProviderCall() throws Exception {
        assertThatThrownBy(() -> service.search(7L, "Gọi 0901234567"))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(SearchErrorCode.SEARCH_QUERY_INVALID));
        verify(aiClient, never()).recommend(any(), any(), any());
    }

    private void ready() {
        when(aiClient.isAvailable()).thenReturn(true);
        when(aiClient.model()).thenReturn("gemini-3.5-flash-lite");
        when(categoryService.listActive()).thenReturn(List.of());
        when(areaService.listTree()).thenReturn(List.of());
        when(configService.getInt(ConfigKey.AI_SEARCH_TIMEOUT_SECONDS)).thenReturn(15);
        when(quotaService.getQuota(7L)).thenReturn(new AiQuotaResponse(4, 2, true));
    }

    private static ItemResponse item(long id) {
        ItemResponse item = mock(ItemResponse.class);
        when(item.id()).thenReturn(id);
        return item;
    }
}
