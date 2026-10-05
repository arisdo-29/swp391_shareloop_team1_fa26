package com.shareloop.item.mapper;

import com.shareloop.item.dto.ItemResponse;
import com.shareloop.item.entity.Item;

public final class ItemMapper {

    private ItemMapper() {}

    public static ItemResponse toResponse(Item item) {
        if (item == null) {
            return null;
        }
        return new ItemResponse(
                item.getId(),
                item.getDonorId(),
                item.getCategoryId(),
                item.getAreaId(),
                item.getOfferType(),
                item.getTitle(),
                item.getDescription(),
                item.getDesiredItem(),
                item.getCondition(),
                item.getDefectNote(),
                item.getBrand(),
                item.getStatus(),
                item.getCreatedAt(),
                item.getUpdatedAt());
    }
}
