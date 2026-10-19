package com.shareloop.search.dto;

import com.shareloop.common.api.PageResponse;
import com.shareloop.item.dto.ItemResponse;

public record AiSearchResponse(
        AiSearchFilters filters,
        PageResponse<ItemResponse> results,
        AiQuotaResponse quota,
        int chargedCredits,
        boolean fallback) {}
