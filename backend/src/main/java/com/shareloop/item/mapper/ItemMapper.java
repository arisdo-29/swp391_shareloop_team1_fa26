package com.shareloop.item.mapper;

import com.shareloop.item.dto.ItemResponse;
import com.shareloop.item.entity.Item;
import com.shareloop.item.entity.OperatingStatus;
import java.util.List;

public final class ItemMapper {

    private ItemMapper() {}

    public static ItemResponse toResponse(Item item) {
        return toResponse(item, List.of());
    }

    public static ItemResponse toResponse(Item item, List<String> images) {
        return toResponse(item, images, List.of(), null);
    }

    public static ItemResponse toResponse(
            Item item, List<String> images, List<String> defectImages, OperatingStatus operatingStatus) {
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
                images != null ? images : List.of(),
                item.getStatus(),
                item.getCreatedAt(),
                item.getUpdatedAt(),
                defectImages != null ? defectImages : List.of(),
                operatingStatus);
    }
}
