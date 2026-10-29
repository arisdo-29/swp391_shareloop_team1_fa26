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
import com.shareloop.catalog.dto.AreaResponse;
import com.shareloop.catalog.dto.CategoryResponse;
import com.shareloop.catalog.service.AreaService;
import com.shareloop.catalog.service.CategoryService;
import com.shareloop.common.api.PageResponse;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.integration.ai.AiResponse;
import com.shareloop.integration.ai.AiSearchClient;
import com.shareloop.item.dto.ItemResponse;
import com.shareloop.item.entity.ItemCondition;
import com.shareloop.item.entity.OfferType;
import com.shareloop.item.service.ItemService;
import com.shareloop.search.SearchErrorCode;
import com.shareloop.search.dto.AiQuotaResponse;
import com.shareloop.search.dto.AiSearchResponse;
import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.service.ConfigService;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class AiSearchServiceTest {
    private static final String MODEL = "cohere/north-mini-code:free";

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
    void validFiltersQueryOnlyTheItemServiceAndLogOneCall() throws Exception {
        ready();
        when(aiClient.extract(eq("Tìm sách cũ ở quận 1"), any(), eq(Duration.ofSeconds(15))))
                .thenReturn(new AiResponse(MODEL, """
                        {"keywords":"sách cũ","categoryId":1,"areaId":11,
                         "condition":"GOOD","offerType":"GIVE"}
                        """));
        PageResponse<ItemResponse> page = new PageResponse<>(List.of(mock(ItemResponse.class)), 0, 20, 1);
        when(itemService.searchApprovedForAi(1L, 11L, "sách cũ", ItemCondition.GOOD, OfferType.GIVE, 0, 20))
                .thenReturn(page);

        AiSearchResponse result = service.search(7L, "Tìm sách cũ ở quận 1", 0, 20);

        assertThat(result.filters().categoryId()).isEqualTo(1L);
        assertThat(result.filters().areaId()).isEqualTo(11L);
        assertThat(result.results()).isSameAs(page);
        assertThat(result.fallback()).isFalse();
        assertThat(result.broadened()).isFalse();
        assertThat(result.quota().freeUsesRemaining()).isEqualTo(4);
        verify(quotaService).checkAvailable(7L);
        verify(quotaService).consume(7L);
        verify(activityLogService).log(eq("AI_USAGE"), eq(7L), eq("AI_SEARCH"), eq(null), anyMap(), eq(null));
    }

    @Test
    void resultAfterFreeAllowanceChargesConfiguredFee() throws Exception {
        ready();
        when(aiClient.extract(any(), any(), any())).thenReturn(new AiResponse(MODEL, """
                {"keywords":"sách","categoryId":null,"areaId":null,
                 "condition":null,"offerType":null}
                """));
        when(itemService.searchApprovedForAi(null, null, "sách", null, null, 0, 20))
                .thenReturn(new PageResponse<>(List.of(mock(ItemResponse.class)), 0, 20, 1));
        when(quotaService.consume(7L)).thenReturn(true);
        when(configService.getInt(ConfigKey.AI_SEARCH_FEE)).thenReturn(2);

        AiSearchResponse result = service.search(7L, "Tìm sách", 0, 20);

        assertThat(result.chargedCredits()).isEqualTo(2);
    }

    @Test
    void retriesKeywordsWhenAiFiltersHideAvailableItems() throws Exception {
        ready();
        when(aiClient.extract(eq("Tìm bàn học"), any(), eq(Duration.ofSeconds(15))))
                .thenReturn(new AiResponse(MODEL, """
                        {"keywords":"bàn học","categoryId":1,"areaId":11,
                         "condition":"NEW","offerType":"GIVE"}
                        """));
        PageResponse<ItemResponse> empty = new PageResponse<>(List.of(), 0, 20, 0);
        PageResponse<ItemResponse> keywordResults = new PageResponse<>(List.of(mock(ItemResponse.class)), 0, 20, 1);
        when(itemService.searchApprovedForAi(1L, 11L, "bàn học", ItemCondition.NEW, OfferType.GIVE, 0, 20))
                .thenReturn(empty);
        when(itemService.searchApprovedForAi(null, null, "bàn học", null, null, 0, 20))
                .thenReturn(keywordResults);

        AiSearchResponse result = service.search(7L, "Tìm bàn học", 0, 20);

        assertThat(result.results()).isSameAs(keywordResults);
        assertThat(result.filters().categoryId()).isEqualTo(1L);
        assertThat(result.filters().areaId()).isEqualTo(11L);
        assertThat(result.broadened()).isTrue();
        assertThat(result.fallback()).isFalse();
        verify(quotaService).consume(7L);
    }

    @Test
    void keepsEmptyResultsWhenKeywordsAlsoFindNothing() throws Exception {
        ready();
        when(aiClient.extract(eq("Tìm bàn học"), any(), eq(Duration.ofSeconds(15))))
                .thenReturn(new AiResponse(MODEL, """
                        {"keywords":"bàn học","categoryId":1,"areaId":null,
                         "condition":null,"offerType":null}
                        """));
        PageResponse<ItemResponse> empty = new PageResponse<>(List.of(), 0, 20, 0);
        when(itemService.searchApprovedForAi(1L, null, "bàn học", null, null, 0, 20))
                .thenReturn(empty);
        when(itemService.searchApprovedForAi(null, null, "bàn học", null, null, 0, 20))
                .thenReturn(empty);

        AiSearchResponse result = service.search(7L, "Tìm bàn học", 0, 20);

        assertThat(result.results()).isSameAs(empty);
        assertThat(result.broadened()).isFalse();
        assertThat(result.chargedCredits()).isZero();
        verify(quotaService, never()).consume(7L);
    }

    @Test
    void doesNotChargeWhenRequestedPageHasNoItems() throws Exception {
        ready();
        when(aiClient.extract(any(), any(), any())).thenReturn(new AiResponse(MODEL, """
                {"keywords":"sách","categoryId":null,"areaId":null,
                 "condition":null,"offerType":null}
                """));
        when(itemService.searchApprovedForAi(null, null, "sách", null, null, 3, 20))
                .thenReturn(new PageResponse<>(List.of(), 3, 20, 1));

        AiSearchResponse result = service.search(7L, "Tìm sách", 3, 20);

        assertThat(result.chargedCredits()).isZero();
        verify(quotaService, never()).consume(7L);
    }

    @Test
    void unknownDistrictFallsBackWithoutConsumingQuota() throws Exception {
        ready();
        when(aiClient.extract(any(), any(), any())).thenReturn(new AiResponse(MODEL, """
                {"keywords":"sách","categoryId":1,"areaId":10,"condition":null,"offerType":null}
                """));
        AiSearchResponse result = service.search(7L, "Tìm sách cũ ở quận 1", 0, 20);
        assertThat(result.fallback()).isTrue();
        verify(quotaService, never()).consume(7L);
        verify(itemService).searchApprovedForAi(null, null, "Tìm sách cũ ở quận 1", null, null, 0, 20);
    }

    @Test
    void timeoutFallsBackWithoutConsumingQuota() throws Exception {
        ready();
        when(aiClient.model()).thenReturn(MODEL);
        when(aiClient.extract(any(), any(), any())).thenThrow(new HttpTimeoutException("timed out"));
        assertThat(service.search(7L, "Tìm sách cũ ở quận 1", 0, 20).fallback()).isTrue();
        verify(quotaService, never()).consume(7L);
    }

    @Test
    void missingApiKeyFallsBackWithoutReserving() throws Exception {
        assertThat(service.search(7L, "Tìm sách cũ ở quận 1", 0, 20).fallback()).isTrue();
        verify(aiClient, never()).extract(any(), any(), any());
        verify(quotaService, never()).checkAvailable(any(Long.class));
    }

    @Test
    void contactDetailsAreRejectedBeforeSendingUserTextToProvider() throws Exception {
        assertThatThrownBy(() -> service.search(7L, "Tìm sách, gọi 0901234567", 0, 20))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(SearchErrorCode.SEARCH_QUERY_INVALID));
        verify(aiClient, never()).extract(any(), any(), any());
    }

    private void ready() {
        when(aiClient.isAvailable()).thenReturn(true);
        when(categoryService.listActive()).thenReturn(List.of(new CategoryResponse(1L, "Sách", null, false, true, 0)));
        when(areaService.listTree())
                .thenReturn(List.of(new AreaResponse(
                        10L,
                        "TP HCM",
                        null,
                        (short) 1,
                        List.of(new AreaResponse(11L, "Quận 1", 10L, (short) 2, List.of())))));
        when(configService.getInt(ConfigKey.AI_SEARCH_TIMEOUT_SECONDS)).thenReturn(15);
        when(quotaService.getQuota(7L)).thenReturn(new AiQuotaResponse(4, 2, true));
    }

    private void assertError(SearchErrorCode expected) {
        assertThatThrownBy(() -> service.search(7L, "Tìm sách cũ ở quận 1", 0, 20))
                .isInstanceOf(BusinessException.class)
                .satisfies(error ->
                        assertThat(((BusinessException) error).getErrorCode()).isEqualTo(expected));
    }
}
