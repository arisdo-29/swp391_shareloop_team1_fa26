package com.shareloop.moderation.event;

import com.shareloop.item.entity.Item;

/** Immutable content snapshot; an old screening result must never overwrite a later edit. */
public record ItemScreeningRequestedEvent(
        long itemId,
        long donorId,
        long categoryId,
        String offerType,
        String title,
        String description,
        String desiredItem,
        String condition) {

    public static ItemScreeningRequestedEvent from(Item item) {
        return new ItemScreeningRequestedEvent(
                item.getId(),
                item.getDonorId(),
                item.getCategoryId(),
                item.getOfferType().name(),
                item.getTitle(),
                item.getDescription(),
                item.getDesiredItem(),
                item.getCondition().name());
    }
}
