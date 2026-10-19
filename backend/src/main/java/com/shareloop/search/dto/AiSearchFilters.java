package com.shareloop.search.dto;

import com.shareloop.item.entity.ItemCondition;
import com.shareloop.item.entity.OfferType;

public record AiSearchFilters(
        String keywords, Long categoryId, Long areaId, ItemCondition condition, OfferType offerType) {}
